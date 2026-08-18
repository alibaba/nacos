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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Config-CAS backed search projection readiness coordination.
 *
 * @author Nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class ConfigAiResourceSearchReadinessService
    implements AiResourceSearchReadinessService {
    
    static final String READINESS_DATA_ID_PREFIX = "nacos.ai.resource.search.readiness.";
    
    static final String READINESS_GROUP = "nacos_internal";
    
    static final String STATE_VERIFYING = "VERIFYING";
    
    static final String STATE_READY = "READY";
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(ConfigAiResourceSearchReadinessService.class);
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final AiResourceIndexTaskRepository taskRepository;
    
    private final Clock clock;
    
    @Autowired
    public ConfigAiResourceSearchReadinessService(
        ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService,
        AiResourceIndexTaskRepository taskRepository) {
        this(configQueryChainService, configOperationService, taskRepository, Clock.systemUTC());
    }
    
    ConfigAiResourceSearchReadinessService(ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService,
        AiResourceIndexTaskRepository taskRepository, Clock clock) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }
    
    @Override
    public boolean isReady(String resourceType, int projectionVersion) {
        if (StringUtils.isBlank(resourceType) || projectionVersion <= 0) {
            return false;
        }
        ReadResult current = read(resourceType, projectionVersion);
        return current.record != null && current.record.matches(resourceType, projectionVersion)
            && STATE_READY.equals(current.record.getState());
    }
    
    @Override
    public void recordCompletedScan(String resourceType, int projectionVersion, boolean clean) {
        if (StringUtils.isBlank(resourceType) || projectionVersion <= 0) {
            return;
        }
        try {
            ReadResult current = read(resourceType, projectionVersion);
            if (current.record != null && current.record.matches(resourceType, projectionVersion)
                && STATE_READY.equals(current.record.getState())) {
                return;
            }
            boolean verifiedBefore = current.record != null
                && current.record.matches(resourceType, projectionVersion)
                && STATE_VERIFYING.equals(current.record.getState());
            boolean ready = clean && verifiedBefore
                && !taskRepository.hasUnfinishedTasks(resourceType);
            ReadinessRecord next = new ReadinessRecord();
            next.setResourceType(resourceType);
            next.setProjectionVersion(projectionVersion);
            next.setState(ready ? STATE_READY : STATE_VERIFYING);
            next.setCompletedAt(ready ? clock.millis() : 0L);
            publish(resourceType, projectionVersion, JacksonUtils.toJson(next), current);
        } catch (Exception e) {
            LOGGER.warn("Failed to advance AI resource search readiness for {} projection {}",
                resourceType, projectionVersion, e);
        }
    }
    
    private ReadResult read(String resourceType, int projectionVersion) {
        try {
            ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                dataId(resourceType, projectionVersion), READINESS_GROUP,
                com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response == null || response
                .getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
                return ReadResult.absent();
            }
            if (StringUtils.isBlank(response.getContent())) {
                return ReadResult.present(null, response.getMd5());
            }
            try {
                return ReadResult.present(JacksonUtils.toObj(response.getContent(),
                    ReadinessRecord.class), response.getMd5());
            } catch (Exception e) {
                LOGGER.warn("Invalid AI resource search readiness record for {} projection {}",
                    resourceType, projectionVersion, e);
                return ReadResult.present(null, response.getMd5());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read AI resource search readiness for {} projection {}",
                resourceType, projectionVersion, e);
            return ReadResult.unavailable();
        }
    }
    
    private void publish(String resourceType, int projectionVersion, String content,
        ReadResult current) throws Exception {
        if (!current.available) {
            return;
        }
        if (current.exists && StringUtils.isBlank(current.md5)) {
            LOGGER.warn("Cannot CAS AI resource search readiness for {} projection {}",
                resourceType, projectionVersion);
            return;
        }
        ConfigForm form = new ConfigForm();
        form.setNamespaceId(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
        form.setGroup(READINESS_GROUP);
        form.setDataId(dataId(resourceType, projectionVersion));
        form.setContent(content);
        form.setSrcUser("nacos");
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        requestInfo.setUpdateForExist(current.exists);
        requestInfo.setCasMd5(current.exists ? current.md5 : null);
        try {
            configOperationService.publishConfig(form, requestInfo, null);
        } catch (ConfigAlreadyExistsException e) {
            LOGGER.debug("AI resource search readiness was concurrently initialized for {}",
                resourceType);
        }
    }
    
    private String dataId(String resourceType, int projectionVersion) {
        return READINESS_DATA_ID_PREFIX + resourceType + ".v" + projectionVersion;
    }
    
    public static class ReadinessRecord {
        
        private String resourceType;
        
        private int projectionVersion;
        
        private String state;
        
        private long completedAt;
        
        public String getResourceType() {
            return resourceType;
        }
        
        public void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }
        
        public int getProjectionVersion() {
            return projectionVersion;
        }
        
        public void setProjectionVersion(int projectionVersion) {
            this.projectionVersion = projectionVersion;
        }
        
        public String getState() {
            return state;
        }
        
        public void setState(String state) {
            this.state = state;
        }
        
        public long getCompletedAt() {
            return completedAt;
        }
        
        public void setCompletedAt(long completedAt) {
            this.completedAt = completedAt;
        }
        
        private boolean matches(String expectedType, int expectedProjectionVersion) {
            return expectedProjectionVersion == projectionVersion
                && expectedType.equals(resourceType);
        }
    }
    
    private static final class ReadResult {
        
        private final boolean available;
        
        private final boolean exists;
        
        private final ReadinessRecord record;
        
        private final String md5;
        
        private ReadResult(boolean available, boolean exists, ReadinessRecord record,
            String md5) {
            this.available = available;
            this.exists = exists;
            this.record = record;
            this.md5 = md5;
        }
        
        private static ReadResult absent() {
            return new ReadResult(true, false, null, null);
        }
        
        private static ReadResult present(ReadinessRecord record, String md5) {
            return new ReadResult(true, true, record, md5);
        }
        
        private static ReadResult unavailable() {
            return new ReadResult(false, false, null, null);
        }
    }
}
