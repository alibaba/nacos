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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Reads the one-way marker that authorizes MCP management requests to use lifecycle rows.
 *
 * <p>The marker is intentionally not written by this component. Until a later cutover phase has
 * verified every safety gate and persists {@link #STATE_LIFECYCLE_MANAGED}, this service remains
 * in SYNCING and the compatibility facade keeps the historical management path authoritative.</p>
 *
 * @author Nacos
 */
@Service
public class McpLifecycleManagementStateService {
    
    public static final String MIGRATION_DATA_ID = "nacos.ai.mcp.resource.migration.v1";
    
    public static final String INTERNAL_GROUP = "nacos_internal";
    
    public static final String STATE_LIFECYCLE_MANAGED = "LIFECYCLE_MANAGED";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(
        McpLifecycleManagementStateService.class);
    
    private static final long SYNCING_REFRESH_INTERVAL_MILLIS = 5000L;
    
    private final ConfigQueryChainService configQueryChainService;
    
    private volatile boolean lifecycleManaged;
    
    private volatile long nextRefreshTime;
    
    public McpLifecycleManagementStateService(ConfigQueryChainService configQueryChainService) {
        this.configQueryChainService = configQueryChainService;
    }
    
    /**
     * Return whether the permanent lifecycle-managed marker has been observed.
     *
     * <p>The positive result is cached forever because the marker is one-way. A missing or invalid
     * marker fails closed to the historical path and is checked again after a short interval.</p>
     *
     * @return {@code true} only after a valid permanent marker is observed
     */
    public boolean isLifecycleManaged() {
        if (lifecycleManaged) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now < nextRefreshTime) {
            return false;
        }
        synchronized (this) {
            if (lifecycleManaged) {
                return true;
            }
            now = System.currentTimeMillis();
            if (now < nextRefreshTime) {
                return false;
            }
            lifecycleManaged = loadMarker();
            nextRefreshTime = now + SYNCING_REFRESH_INTERVAL_MILLIS;
            return lifecycleManaged;
        }
    }
    
    private boolean loadMarker() {
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
            MIGRATION_DATA_ID, INTERNAL_GROUP,
            com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
        try {
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response == null || McpConfigUtils.isConfigNotFound(response.getStatus())) {
                return false;
            }
            if (!McpConfigUtils.isConfigFound(response.getStatus())) {
                LOGGER.warn("Unable to read MCP lifecycle management marker: {}",
                    response.getMessage());
                return false;
            }
            Map<?, ?> marker = JacksonUtils.toObj(response.getContent(), Map.class);
            return marker != null && Integer.valueOf(1).equals(asInteger(
                marker.get("schemaVersion")))
                && STATE_LIFECYCLE_MANAGED.equals(marker.get("state"));
        } catch (Exception e) {
            LOGGER.warn("Unable to decode MCP lifecycle management marker", e);
            return false;
        }
    }
    
    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
