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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.event.AiResourceChangeOperation;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agent.storage.PreparedAgentVersionWrite;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceChangeNotifier;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.utils.A2aSecurityUtils;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Best-effort, access-driven repair of historical Card security encodings.
 *
 * <p>Tasks only operate on raw persisted definitions. They never persist runtime query projections
 * or invoke Agent lifecycle updates. Conflicts and failed writes are left for a later read.</p>
 *
 * @author nacos
 */
@Component
public class A2aSecurityReadRepair implements DisposableBean {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(A2aSecurityReadRepair.class);
    
    private static final int CAPACITY = 64;
    
    private final ConfigInfoPersistService configStore;
    
    private final AiResourceVersionPersistService versionStore;
    
    private final AgentVersionStorageService storageService;
    
    private final AiResourceChangeNotifier notifier;
    
    private final ExecutorService executor;
    
    private final Set<String> pending = new HashSet<>();
    
    @Autowired
    public A2aSecurityReadRepair(ConfigInfoPersistService configStore,
        AiResourceVersionPersistService versionStore, AgentVersionStorageService storageService,
        AiResourceChangeNotifier notifier) {
        this(configStore, versionStore, storageService, notifier,
            ExecutorFactory.Managed.newSingleExecutorService(A2aSecurityReadRepair.class.getName(),
                new ThreadFactoryBuilder().daemon(true).nameFormat("nacos-a2a-security-repair-%d")
                    .build()));
    }
    
    A2aSecurityReadRepair(ConfigInfoPersistService configStore,
        AiResourceVersionPersistService versionStore, AgentVersionStorageService storageService,
        AiResourceChangeNotifier notifier, ExecutorService executor) {
        this.configStore = configStore;
        this.versionStore = versionStore;
        this.storageService = storageService;
        this.notifier = notifier;
        this.executor = executor;
    }
    
    /**
     * Normalize one historical Version Config and queue an update-only repair when needed.
     *
     * @param namespaceId namespace identifier
     * @param dataId exact historical Version Config id
     * @param original unmodified persisted JSON
     * @return normalized JSON for immediate POJO binding
     */
    public String readLegacy(String namespaceId, String dataId, String original) {
        Map<String, Object> card = JsonUtils.toObj(original, Map.class);
        if (!A2aSecurityUtils.normalize(card)) {
            return original;
        }
        String replacement = JsonUtils.toJson(card);
        String expectedMd5 = MD5Utils.md5Hex(original, "UTF-8");
        submit("legacy:" + namespaceId.length() + ':' + namespaceId + dataId,
            () -> repairLegacy(namespaceId, dataId, expectedMd5, replacement));
        return replacement;
    }
    
    /**
     * Normalize a verified, caller-owned canonical definition before returning it, then repair storage.
     *
     * @param row original Version row
     * @param descriptor descriptor used to verify the original bytes
     * @param content verified and caller-owned definition
     */
    public void readCanonical(AiResourceVersion row, AgentVersionStorageDescriptor descriptor,
        AgentVersionContent content) {
        if (!normalizeContent(content)) {
            return;
        }
        scheduleCanonical(row, descriptor, content);
    }
    
    /**
     * Normalize a verified definition for a read-only projection, without scheduling storage writes.
     *
     * @param content verified, caller-owned content
     * @return whether Card security fields changed
     */
    public static boolean normalizeContent(AgentVersionContent content) {
        boolean changed = false;
        for (AgentCallInterface call : content.getCallInterfaces()) {
            if ("a2a".equals(call.getProtocol()) && call.getNativeDescriptor() instanceof Map) {
                changed |=
                    A2aSecurityUtils.normalize((Map<String, Object>) call.getNativeDescriptor());
            }
        }
        return changed;
    }
    
    private void scheduleCanonical(AiResourceVersion row, AgentVersionStorageDescriptor descriptor,
        AgentVersionContent content) {
        try {
            PreparedAgentVersionWrite prepared = storageService.prepareRepair(descriptor,
                row.getNamespaceId(), row.getName(), content);
            long id = row.getId();
            String expectedStorage = row.getStorage();
            String namespaceId = row.getNamespaceId();
            String name = row.getName();
            String version = row.getVersion();
            submit("canonical:" + id, () -> repairCanonical(id, namespaceId, name, version,
                expectedStorage, prepared));
        } catch (RuntimeException e) {
            warnFailure(e);
        }
    }
    
    private void repairLegacy(String namespaceId, String dataId, String expectedMd5,
        String replacement) {
        ConfigAllInfo original = configStore.findConfigAllInfo(dataId,
            Constants.A2A.AGENT_VERSION_GROUP, namespaceId);
        if (original == null || !expectedMd5.equals(original.getMd5())) {
            return;
        }
        ConfigInfo update = new ConfigInfo(dataId, original.getGroup(), original.getTenant(),
            original.getAppName(), replacement);
        update.setMd5(expectedMd5);
        update.setEncryptedDataKey(original.getEncryptedDataKey());
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("desc", original.getDesc());
        attributes.put("use", original.getUse());
        attributes.put("effect", original.getEffect());
        attributes.put("type", original.getType());
        attributes.put("schema", original.getSchema());
        ConfigOperateResult result = configStore.updateConfigInfoCas(update, null,
            "nacos-a2a-security-repair", attributes);
        if (result != null && result.isSuccess()) {
            ConfigChangePublisher.notifyConfigChange(new ConfigDataChangeEvent(dataId,
                original.getGroup(), original.getTenant(), result.getLastModified()));
        }
    }
    
    private void repairCanonical(long id, String namespaceId, String name, String version,
        String expectedStorage, PreparedAgentVersionWrite prepared) throws Exception {
        AiResourceVersion current = versionStore.find(namespaceId, name,
            Constants.Agent.RESOURCE_TYPE_AGENT, version);
        if (current == null || !Objects.equals(current.getId(), id)
            || !expectedStorage.equals(current.getStorage())) {
            return;
        }
        storageService.save(prepared);
        // Verify the new bytes before publishing their pointer. No currently referenced bytes changed.
        storageService.load(prepared.getDescriptor());
        String replacement =
            AgentVersionStorageDescriptorSerializer.serialize(prepared.getDescriptor());
        if (versionStore.replaceStorageIfUnchanged(id, expectedStorage, replacement)) {
            notifier.notifyChanged(namespaceId, Constants.Agent.RESOURCE_TYPE_AGENT, name,
                AiResourceChangeOperation.UPDATE, true);
        }
        // Never delete either object on a conflict or uncertain result: another writer/reader may own it.
    }
    
    private void submit(String key, Repair task) {
        synchronized (pending) {
            if (pending.contains(key) || pending.size() >= CAPACITY) {
                return;
            }
            pending.add(key);
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    warnFailure(e);
                } finally {
                    synchronized (pending) {
                        pending.remove(key);
                    }
                }
            });
        } catch (RuntimeException e) {
            synchronized (pending) {
                pending.remove(key);
            }
            warnFailure(e);
        }
    }
    
    private static void warnFailure(Exception exception) {
        // Parser/provider messages can contain descriptor values. Do not log authentication declarations.
        LOGGER.warn("A2A security encoding read repair failed ({}); a later read may retry",
            exception.getClass().getSimpleName());
    }
    
    @Override
    public void destroy() {
        executor.shutdownNow();
        synchronized (pending) {
            pending.clear();
        }
    }
    
    @FunctionalInterface
    private interface Repair {
        
        void run() throws Exception;
    }
}
