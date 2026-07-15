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
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.ai.utils.SkillSeedArchiveReader;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bootstrap built-in skills into an empty AI namespace after cluster startup.
 *
 * @author nacos
 */
@Component
public class SkillDataBootstrapInitializer implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(SkillDataBootstrapInitializer.class);
    
    private static final String BOOTSTRAP_MARKER_DATA_ID = "nacos.ai.skills.bootstrap";
    
    private static final String BOOTSTRAP_MARKER_GROUP = "nacos_internal";
    
    private static final long BOOTSTRAP_MARKER_STALE_MILLIS = 10 * 60 * 1000L;
    
    private static final String RESOURCE_TYPE_SKILL = "skill";
    
    private static final int MAX_BOOTSTRAP_IMPORT_CONCURRENCY = 4;
    
    private static final int BOOTSTRAP_IMPORT_CONCURRENCY = Math.max(1,
        Math.min(EnvUtil.getAvailableProcessors(), MAX_BOOTSTRAP_IMPORT_CONCURRENCY));
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private final ExecutorService bootstrapExecutor =
        ExecutorFactory.Managed.newSingleExecutorService(
            SkillDataBootstrapInitializer.class.getCanonicalName(),
            new ThreadFactoryBuilder().daemon(true).nameFormat("nacos-ai-skill-bootstrap-%d")
                .build());
    
    private final ExecutorService bootstrapImportExecutor =
        ExecutorFactory.Managed.newFixedExecutorService(
            SkillDataBootstrapInitializer.class.getCanonicalName(), BOOTSTRAP_IMPORT_CONCURRENCY,
            new ThreadFactoryBuilder().daemon(true).nameFormat("nacos-ai-skill-bootstrap-import-%d")
                .build());
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final SkillOperationService skillOperationService;
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    public SkillDataBootstrapInitializer(AiResourcePersistService aiResourcePersistService,
        SkillOperationService skillOperationService,
        ConfigOperationService configOperationService,
        ConfigQueryChainService configQueryChainService) {
        this.aiResourcePersistService = aiResourcePersistService;
        this.skillOperationService = skillOperationService;
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
        bootstrapExecutor.execute(this::bootstrapBuiltInSkills);
    }
    
    private void bootstrapBuiltInSkills() {
        boolean markerCreated = false;
        try {
            Resource bundledSkillsArchive = resolveBundledSkillsArchive();
            if (!bundledSkillsArchive.exists()) {
                LOGGER.warn("Built-in skill archive `{}` not found, skip bootstrap",
                    bundledSkillsArchive);
                return;
            }
            
            List<SkillSeedArchiveReader.SkillPackage> skillPackages = readSkillPackages();
            if (skillPackages.isEmpty()) {
                LOGGER.info("No built-in skills found in archive `{}`", bundledSkillsArchive);
                return;
            }
            BootstrapPlan bootstrapPlan = buildBootstrapPlan(skillPackages);
            if (!bootstrapPlan.shouldBootstrap()) {
                LOGGER.info(bootstrapPlan.getSkipReason());
                return;
            }
            
            markerCreated = tryAcquireBootstrapMarker();
            if (!markerCreated) {
                LOGGER.info("Skip built-in skill bootstrap because another node is initializing");
                return;
            }
            
            bootstrapPlan = buildBootstrapPlan(skillPackages);
            if (!bootstrapPlan.shouldBootstrap()) {
                LOGGER.info(bootstrapPlan.getSkipReason());
                return;
            }
            
            int imported = 0;
            int skipped = bootstrapPlan.getExistingBuiltInCount();
            List<String> failedSkills = new ArrayList<>();
            LOGGER.info(
                "Start built-in skill bootstrap in namespace `{}`, total {}, missing {}, existing {}, concurrency {}",
                Constants.DEFAULT_NAMESPACE_ID, skillPackages.size(),
                bootstrapPlan.getMissingSkillPackages().size(),
                bootstrapPlan.getExistingBuiltInCount(), BOOTSTRAP_IMPORT_CONCURRENCY);
            List<Future<ImportTaskResult>> futures =
                new ArrayList<>(bootstrapPlan.getMissingSkillPackages().size());
            for (SkillSeedArchiveReader.SkillPackage skillPackage : bootstrapPlan
                .getMissingSkillPackages()) {
                futures.add(bootstrapImportExecutor.submit(() -> importBuiltInSkill(skillPackage)));
            }
            for (Future<ImportTaskResult> future : futures) {
                ImportTaskResult taskResult = future.get();
                if (taskResult.isSuccess()) {
                    imported++;
                } else {
                    failedSkills.add(taskResult.getSkillName());
                }
            }
            LOGGER.info("Built-in skill bootstrap completed, imported {}, skipped {}, failed {}",
                imported, skipped,
                failedSkills.size());
            if (!failedSkills.isEmpty()) {
                LOGGER.warn("Built-in skill bootstrap still missing {} skills: {}",
                    failedSkills.size(), failedSkills);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to bootstrap built-in skills", e);
        } finally {
            if (markerCreated) {
                releaseBootstrapMarker();
            }
        }
    }
    
    private List<SkillSeedArchiveReader.SkillPackage> readSkillPackages() throws IOException {
        Resource bundledSkillsArchive = resolveBundledSkillsArchive();
        try (InputStream inputStream = bundledSkillsArchive.getInputStream()) {
            return SkillSeedArchiveReader.read(inputStream);
        }
    }
    
    private Resource resolveBundledSkillsArchive() {
        Path archivePath = Paths.get(EnvUtil.getNacosHome(), "data", "skills-data.zip");
        return new FileSystemResource(archivePath);
    }
    
    private ImportTaskResult importBuiltInSkill(SkillSeedArchiveReader.SkillPackage skillPackage) {
        try {
            LOGGER.info("Import built-in skill `{}` from `{}`", skillPackage.getSkillName(),
                skillPackage.getSourcePath());
            skillOperationService.bootstrapSkillFromZip(Constants.DEFAULT_NAMESPACE_ID,
                skillPackage.getZipBytes(),
                skillPackage.getFrom());
            return ImportTaskResult.success(skillPackage.getSkillName());
        } catch (Exception e) {
            LOGGER.error("Failed to bootstrap built-in skill `{}` from `{}`",
                skillPackage.getSkillName(),
                skillPackage.getSourcePath(), e);
            return ImportTaskResult.failed(skillPackage.getSkillName());
        }
    }
    
    private boolean hasExistingAiData() {
        Page<AiResource> page =
            aiResourcePersistService.list(Constants.DEFAULT_NAMESPACE_ID, null, null, null, 1, 1);
        return page != null && page.getTotalCount() > 0;
    }
    
    private BootstrapPlan buildBootstrapPlan(
        List<SkillSeedArchiveReader.SkillPackage> skillPackages) {
        boolean existingAiData = hasExistingAiData();
        int existingBuiltInCount = 0;
        List<SkillSeedArchiveReader.SkillPackage> missingSkillPackages =
            new ArrayList<>(skillPackages.size());
        for (SkillSeedArchiveReader.SkillPackage skillPackage : skillPackages) {
            if (aiResourcePersistService.find(Constants.DEFAULT_NAMESPACE_ID,
                skillPackage.getSkillName(),
                RESOURCE_TYPE_SKILL) != null) {
                existingBuiltInCount++;
            } else {
                missingSkillPackages.add(skillPackage);
            }
        }
        if (missingSkillPackages.isEmpty()) {
            return BootstrapPlan.skip(
                "Skip built-in skill bootstrap because all bundled skills already exist",
                existingBuiltInCount);
        }
        if (!existingAiData) {
            return BootstrapPlan.bootstrap(missingSkillPackages, existingBuiltInCount);
        }
        if (existingBuiltInCount == 0) {
            return BootstrapPlan.skip(
                "Skip built-in skill bootstrap because AI data already exists and no built-in skills were imported before",
                0);
        }
        return BootstrapPlan.bootstrap(missingSkillPackages, existingBuiltInCount);
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
                        "Found stale built-in skill bootstrap marker, removing it and retrying");
                    releaseBootstrapMarker();
                    continue;
                }
                return false;
            } catch (Exception e) {
                LOGGER.error("Failed to create built-in skill bootstrap marker", e);
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
            LOGGER.warn("Failed to inspect built-in skill bootstrap marker", e);
            return false;
        }
    }
    
    private void releaseBootstrapMarker() {
        try {
            configOperationService.deleteConfig(BOOTSTRAP_MARKER_DATA_ID, BOOTSTRAP_MARKER_GROUP,
                Constants.DEFAULT_NAMESPACE_ID, null, null, "nacos", null);
        } catch (Exception e) {
            LOGGER.warn("Failed to delete built-in skill bootstrap marker", e);
        }
    }
    
    private static final class BootstrapPlan {
        
        private final boolean shouldBootstrap;
        
        private final String skipReason;
        
        private final List<SkillSeedArchiveReader.SkillPackage> missingSkillPackages;
        
        private final int existingBuiltInCount;
        
        private BootstrapPlan(boolean shouldBootstrap, String skipReason,
            List<SkillSeedArchiveReader.SkillPackage> missingSkillPackages,
            int existingBuiltInCount) {
            this.shouldBootstrap = shouldBootstrap;
            this.skipReason = skipReason;
            this.missingSkillPackages = missingSkillPackages;
            this.existingBuiltInCount = existingBuiltInCount;
        }
        
        private static BootstrapPlan bootstrap(
            List<SkillSeedArchiveReader.SkillPackage> missingSkillPackages,
            int existingBuiltInCount) {
            return new BootstrapPlan(true, null, missingSkillPackages, existingBuiltInCount);
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
        
        private List<SkillSeedArchiveReader.SkillPackage> getMissingSkillPackages() {
            return missingSkillPackages;
        }
        
        private int getExistingBuiltInCount() {
            return existingBuiltInCount;
        }
    }
    
    private static final class ImportTaskResult {
        
        private final String skillName;
        
        private final boolean success;
        
        private ImportTaskResult(String skillName, boolean success) {
            this.skillName = skillName;
            this.success = success;
        }
        
        private static ImportTaskResult success(String skillName) {
            return new ImportTaskResult(skillName, true);
        }
        
        private static ImportTaskResult failed(String skillName) {
            return new ImportTaskResult(skillName, false);
        }
        
        private String getSkillName() {
            return skillName;
        }
        
        private boolean isSuccess() {
            return success;
        }
    }
}
