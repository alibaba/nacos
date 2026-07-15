/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationService;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.utils.AgentSpecSeedArchiveReader;
import com.alibaba.nacos.ai.utils.AgentSpecZipParser;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bootstrap built-in AgentSpecs into an empty AI namespace after cluster startup.
 *
 * @author nacos
 */
@Component
public class AgentSpecDataBootstrapInitializer
    implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AgentSpecDataBootstrapInitializer.class);
    
    private static final String BOOTSTRAP_MARKER_DATA_ID = "nacos.ai.agentspec.bootstrap";
    
    private static final String BOOTSTRAP_MARKER_GROUP = "nacos_internal";
    
    private static final long BOOTSTRAP_MARKER_STALE_MILLIS = 10 * 60 * 1000L;
    
    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    private static final int MAX_BOOTSTRAP_IMPORT_CONCURRENCY = 4;
    
    private static final int BOOTSTRAP_IMPORT_CONCURRENCY = Math.max(1,
        Math.min(EnvUtil.getAvailableProcessors(), MAX_BOOTSTRAP_IMPORT_CONCURRENCY));
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private final ExecutorService bootstrapExecutor =
        ExecutorFactory.Managed.newSingleExecutorService(
            AgentSpecDataBootstrapInitializer.class.getCanonicalName(),
            new ThreadFactoryBuilder().daemon(true).nameFormat("nacos-ai-agentspec-bootstrap-%d")
                .build());
    
    private final ExecutorService bootstrapImportExecutor =
        ExecutorFactory.Managed.newFixedExecutorService(
            AgentSpecDataBootstrapInitializer.class.getCanonicalName(),
            BOOTSTRAP_IMPORT_CONCURRENCY,
            new ThreadFactoryBuilder().daemon(true)
                .nameFormat("nacos-ai-agentspec-bootstrap-import-%d").build());
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final AgentSpecOperationService agentSpecOperationService;
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    public AgentSpecDataBootstrapInitializer(AiResourcePersistService aiResourcePersistService,
        AgentSpecOperationService agentSpecOperationService,
        ConfigOperationService configOperationService,
        ConfigQueryChainService configQueryChainService) {
        this.aiResourcePersistService = aiResourcePersistService;
        this.agentSpecOperationService = agentSpecOperationService;
        this.configOperationService = configOperationService;
        this.configQueryChainService = configQueryChainService;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        bootstrapExecutor.execute(this::bootstrapBuiltInAgentSpecs);
    }
    
    private void bootstrapBuiltInAgentSpecs() {
        boolean markerCreated = false;
        try {
            Resource bundledAgentSpecArchive = resolveBundledAgentSpecArchive();
            if (!bundledAgentSpecArchive.exists()) {
                LOGGER.warn("Built-in agentspec archive `{}` not found, skip bootstrap",
                    bundledAgentSpecArchive);
                return;
            }
            
            List<AgentSpecSeedArchiveReader.AgentSpecPackage> packages = readAgentSpecPackages();
            if (packages.isEmpty()) {
                LOGGER.info("No built-in agentspecs found in archive `{}`",
                    bundledAgentSpecArchive);
                return;
            }
            BootstrapPlan bootstrapPlan = buildBootstrapPlan(packages);
            if (!bootstrapPlan.shouldBootstrap()) {
                LOGGER.info(bootstrapPlan.getSkipReason());
                return;
            }
            
            markerCreated = tryAcquireBootstrapMarker();
            if (!markerCreated) {
                LOGGER
                    .info("Skip built-in agentspec bootstrap because another node is initializing");
                return;
            }
            
            bootstrapPlan = buildBootstrapPlan(packages);
            if (!bootstrapPlan.shouldBootstrap()) {
                LOGGER.info(bootstrapPlan.getSkipReason());
                return;
            }
            
            int imported = 0;
            int skipped = bootstrapPlan.getExistingBuiltInCount();
            List<String> failed = new ArrayList<>();
            LOGGER.info(
                "Start built-in agentspec bootstrap in namespace `{}`, total {}, missing {}, existing {}, concurrency {}",
                Constants.DEFAULT_NAMESPACE_ID, packages.size(),
                bootstrapPlan.getMissingPackages().size(),
                bootstrapPlan.getExistingBuiltInCount(), BOOTSTRAP_IMPORT_CONCURRENCY);
            List<Future<ImportTaskResult>> futures =
                new ArrayList<>(bootstrapPlan.getMissingPackages().size());
            for (AgentSpecSeedArchiveReader.AgentSpecPackage pkg : bootstrapPlan
                .getMissingPackages()) {
                futures.add(bootstrapImportExecutor.submit(() -> importBuiltInAgentSpec(pkg)));
            }
            for (Future<ImportTaskResult> future : futures) {
                ImportTaskResult taskResult = future.get();
                if (taskResult.isSuccess()) {
                    imported++;
                } else {
                    failed.add(taskResult.getAgentSpecName());
                }
            }
            LOGGER.info(
                "Built-in agentspec bootstrap completed, imported {}, skipped {}, failed {}",
                imported, skipped,
                failed.size());
            if (!failed.isEmpty()) {
                LOGGER.warn("Built-in agentspec bootstrap still missing {} agentspecs: {}",
                    failed.size(), failed);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to bootstrap built-in agentspecs", e);
        } finally {
            if (markerCreated) {
                releaseBootstrapMarker();
            }
        }
    }
    
    private List<AgentSpecSeedArchiveReader.AgentSpecPackage> readAgentSpecPackages()
        throws IOException {
        Resource bundledAgentSpecArchive = resolveBundledAgentSpecArchive();
        try (InputStream inputStream = bundledAgentSpecArchive.getInputStream()) {
            return AgentSpecSeedArchiveReader.read(inputStream);
        }
    }
    
    private Resource resolveBundledAgentSpecArchive() {
        Path archivePath = Paths.get(EnvUtil.getNacosHome(), "data", "agentspec-data.zip");
        return new FileSystemResource(archivePath);
    }
    
    private ImportTaskResult importBuiltInAgentSpec(
        AgentSpecSeedArchiveReader.AgentSpecPackage pkg) {
        try {
            LOGGER.info("Import built-in agentspec `{}` from `{}`", pkg.getAgentSpecName(),
                pkg.getSourcePath());
            agentSpecOperationService.bootstrapAgentSpecFromZip(Constants.DEFAULT_NAMESPACE_ID,
                pkg.getZipBytes(),
                pkg.getFrom());
            return ImportTaskResult.success(pkg.getAgentSpecName());
        } catch (Exception e) {
            LOGGER.error("Failed to bootstrap built-in agentspec `{}` from `{}`",
                pkg.getAgentSpecName(),
                pkg.getSourcePath(), e);
            return ImportTaskResult.failed(pkg.getAgentSpecName());
        }
    }
    
    private boolean hasExistingAiData() {
        Page<AiResource> page =
            aiResourcePersistService.list(Constants.DEFAULT_NAMESPACE_ID, null, null, null, 1, 1);
        return page != null && page.getTotalCount() > 0;
    }
    
    private BootstrapPlan buildBootstrapPlan(
        List<AgentSpecSeedArchiveReader.AgentSpecPackage> packages) {
        boolean existingAiData = hasExistingAiData();
        int existingBuiltInCount = 0;
        List<AgentSpecSeedArchiveReader.AgentSpecPackage> missing =
            new ArrayList<>(packages.size());
        for (AgentSpecSeedArchiveReader.AgentSpecPackage pkg : packages) {
            if (aiResourcePersistService.find(Constants.DEFAULT_NAMESPACE_ID,
                pkg.getAgentSpecName(),
                RESOURCE_TYPE_AGENTSPEC) != null) {
                existingBuiltInCount++;
                if (needsBuiltInRepair(pkg)) {
                    missing.add(pkg);
                }
            } else {
                missing.add(pkg);
            }
        }
        if (missing.isEmpty()) {
            return BootstrapPlan.skip(
                "Skip built-in agentspec bootstrap because all bundled agentspecs already exist",
                existingBuiltInCount);
        }
        if (!existingAiData) {
            return BootstrapPlan.bootstrap(missing, existingBuiltInCount);
        }
        if (existingBuiltInCount == 0) {
            return BootstrapPlan.skip(
                "Skip built-in agentspec bootstrap because AI data already exists and no built-in agentspecs were imported before",
                0);
        }
        return BootstrapPlan.bootstrap(missing, existingBuiltInCount);
    }
    
    private boolean needsBuiltInRepair(AgentSpecSeedArchiveReader.AgentSpecPackage pkg) {
        try {
            AgentSpec bundled = AgentSpecZipParser.parseAgentSpecFromZip(pkg.getZipBytes(),
                Constants.DEFAULT_NAMESPACE_ID);
            if (bundled.getResource() == null || bundled.getResource().isEmpty()) {
                return false;
            }
            AgentSpecMeta detail =
                agentSpecOperationService.getAgentSpecDetail(Constants.DEFAULT_NAMESPACE_ID,
                    pkg.getAgentSpecName());
            String latestVersion = detail == null || detail.getLabels() == null ? null
                : detail.getLabels().get("latest");
            if (StringUtils.isBlank(latestVersion)) {
                return true;
            }
            AgentSpec current =
                agentSpecOperationService.getAgentSpecVersionDetail(Constants.DEFAULT_NAMESPACE_ID,
                    pkg.getAgentSpecName(), latestVersion);
            return isBundledAgentsContentMissing(current, bundled);
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to inspect built-in agentspec `{}` for repair, will retry bootstrap import",
                pkg.getAgentSpecName(), e);
            return true;
        }
    }
    
    private static boolean isBundledAgentsContentMissing(AgentSpec current, AgentSpec bundled) {
        if (bundled == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        String bundledAgentsContent = extractAgentsContent(bundled.getResource());
        if (StringUtils.isBlank(bundledAgentsContent)) {
            return false;
        }
        String currentAgentsContent = extractAgentsContent(current.getResource());
        return StringUtils.isBlank(currentAgentsContent);
    }
    
    private static String extractAgentsContent(Map<String, AgentSpecResource> resources) {
        if (resources == null || resources.isEmpty()) {
            return null;
        }
        for (AgentSpecResource resource : resources.values()) {
            if (resource == null || StringUtils.isBlank(resource.getName())) {
                continue;
            }
            String normalizedName = resource.getName().trim();
            int lastSlash = normalizedName.lastIndexOf('/');
            if (lastSlash >= 0) {
                normalizedName = normalizedName.substring(lastSlash + 1);
            }
            if ("AGENTS.md".equalsIgnoreCase(normalizedName)) {
                return resource.getContent();
            }
        }
        return null;
    }
    
    private boolean tryAcquireBootstrapMarker() {
        for (int i = 0; i < 2; i++) {
            try {
                ConfigForm form = new ConfigForm();
                form.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
                form.setGroup(BOOTSTRAP_MARKER_GROUP);
                form.setDataId(BOOTSTRAP_MARKER_DATA_ID);
                form.setContent(String.valueOf(System.currentTimeMillis()));
                form.setSrcUser("nacos");
                ConfigRequestInfo requestInfo = new ConfigRequestInfo();
                requestInfo.setUpdateForExist(false);
                configOperationService.publishConfig(form, requestInfo, null);
                return true;
            } catch (ConfigAlreadyExistsException e) {
                if (!hasExistingAiData() && isBootstrapMarkerStale()) {
                    LOGGER.warn(
                        "Found stale built-in agentspec bootstrap marker, removing it and retrying");
                    releaseBootstrapMarker();
                    continue;
                }
                return false;
            } catch (Exception e) {
                LOGGER.error("Failed to create built-in agentspec bootstrap marker", e);
                return false;
            }
        }
        return false;
    }
    
    private boolean isBootstrapMarkerStale() {
        try {
            ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                BOOTSTRAP_MARKER_DATA_ID, BOOTSTRAP_MARKER_GROUP, Constants.DEFAULT_NAMESPACE_ID);
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND
                || StringUtils.isBlank(response.getContent())) {
                return false;
            }
            long markerTime = Long.parseLong(response.getContent().trim());
            return System.currentTimeMillis() - markerTime > BOOTSTRAP_MARKER_STALE_MILLIS;
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect built-in agentspec bootstrap marker", e);
            return false;
        }
    }
    
    private void releaseBootstrapMarker() {
        try {
            configOperationService.deleteConfig(BOOTSTRAP_MARKER_DATA_ID, BOOTSTRAP_MARKER_GROUP,
                Constants.DEFAULT_NAMESPACE_ID, null, null, "nacos", null);
        } catch (Exception e) {
            LOGGER.warn("Failed to delete built-in agentspec bootstrap marker", e);
        }
    }
    
    private static final class BootstrapPlan {
        
        private final boolean shouldBootstrap;
        
        private final String skipReason;
        
        private final List<AgentSpecSeedArchiveReader.AgentSpecPackage> missingPackages;
        
        private final int existingBuiltInCount;
        
        private BootstrapPlan(boolean shouldBootstrap, String skipReason,
            List<AgentSpecSeedArchiveReader.AgentSpecPackage> missingPackages,
            int existingBuiltInCount) {
            this.shouldBootstrap = shouldBootstrap;
            this.skipReason = skipReason;
            this.missingPackages = missingPackages;
            this.existingBuiltInCount = existingBuiltInCount;
        }
        
        private static BootstrapPlan bootstrap(
            List<AgentSpecSeedArchiveReader.AgentSpecPackage> missingPackages,
            int existingBuiltInCount) {
            return new BootstrapPlan(true, null, missingPackages, existingBuiltInCount);
        }
        
        private static BootstrapPlan skip(String skipReason, int existingBuiltInCount) {
            return new BootstrapPlan(false, skipReason, new ArrayList<>(0), existingBuiltInCount);
        }
        
        private boolean shouldBootstrap() {
            return shouldBootstrap;
        }
        
        private String getSkipReason() {
            return skipReason;
        }
        
        private List<AgentSpecSeedArchiveReader.AgentSpecPackage> getMissingPackages() {
            return missingPackages;
        }
        
        private int getExistingBuiltInCount() {
            return existingBuiltInCount;
        }
    }
    
    private static final class ImportTaskResult {
        
        private final String agentSpecName;
        
        private final boolean success;
        
        private ImportTaskResult(String agentSpecName, boolean success) {
            this.agentSpecName = agentSpecName;
            this.success = success;
        }
        
        private static ImportTaskResult success(String agentSpecName) {
            return new ImportTaskResult(agentSpecName, true);
        }
        
        private static ImportTaskResult failed(String agentSpecName) {
            return new ImportTaskResult(agentSpecName, false);
        }
        
        private String getAgentSpecName() {
            return agentSpecName;
        }
        
        private boolean isSuccess() {
            return success;
        }
    }
}
