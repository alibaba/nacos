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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.search.AiResourceSearchReadinessService;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the one-way MCP lifecycle management marker and its cluster cutover gates.
 *
 * <p>The permanent marker changes only the MCP management authority. Historical serving
 * Config and Naming data remain untouched.</p>
 *
 * @author Nacos
 */
@Service
public class McpLifecycleManagementStateService {
    
    public static final String MIGRATION_MARKER_DATA_ID =
        "nacos.ai.mcp.resource.migration.v1";
    
    public static final String LIFECYCLE_MANAGED_STATE = "LIFECYCLE_MANAGED";
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(McpLifecycleManagementStateService.class);
    
    private static final int MARKER_SCHEMA_VERSION = 1;
    
    private static final int MCP_SEARCH_PROJECTION_VERSION = 2;
    
    private static final String INTERNAL_GROUP = "nacos_internal";
    
    private static final long MARKER_REFRESH_INTERVAL_MILLIS = 3000L;
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final ServerMemberManager serverMemberManager;
    
    private final AiResourceSearchReadinessService searchReadinessService;
    
    private final AtomicBoolean lifecycleManaged = new AtomicBoolean(false);
    
    private volatile long nextMarkerRefreshAt;
    
    @Autowired
    public McpLifecycleManagementStateService(ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService, ServerMemberManager serverMemberManager,
        ObjectProvider<AiResourceSearchReadinessService> searchReadinessServiceProvider) {
        this(configQueryChainService, configOperationService, serverMemberManager,
            searchReadinessServiceProvider.getIfAvailable(
                () -> AiResourceSearchReadinessService.NOOP));
    }
    
    McpLifecycleManagementStateService(ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService, ServerMemberManager serverMemberManager,
        AiResourceSearchReadinessService searchReadinessService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.serverMemberManager = serverMemberManager;
        this.searchReadinessService = searchReadinessService;
    }
    
    /**
     * Resolve the durable management mode. Once observed, managed mode is latched in memory.
     *
     * @return current management mode
     */
    public McpCompatibilityMode resolveMode() {
        if (lifecycleManaged.get()) {
            return McpCompatibilityMode.LIFECYCLE_MANAGED;
        }
        refreshMarkerIfNecessary(false);
        return lifecycleManaged.get() ? McpCompatibilityMode.LIFECYCLE_MANAGED
            : McpCompatibilityMode.SYNCING;
    }
    
    /**
     * Check whether this member can safely serve MCP management requests after cutover.
     *
     * @return {@code true} when the local member advertises managed lifecycle support
     */
    public boolean localMemberSupportsManagedLifecycle() {
        try {
            Member self = serverMemberManager.getSelf();
            return self != null && Boolean.TRUE.equals(
                self.getExtendVal(MemberMetaDataConstants.SUPPORT_MCP_LIFECYCLE_MANAGEMENT));
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect local MCP lifecycle management capability", e);
            return false;
        }
    }
    
    /**
     * Evaluate all cutover gates and persist the permanent marker when they are satisfied.
     *
     * @param zeroDifference whether the last complete reconciliation found no differences or
     *                       pending cleanup
     * @return gate and marker outcome
     */
    public synchronized CutoverStatus tryCompleteCutover(boolean zeroDifference) {
        if (resolveMode() == McpCompatibilityMode.LIFECYCLE_MANAGED) {
            return CutoverStatus.managed();
        }
        boolean membersReady = allMembersSupportManagedLifecycle();
        boolean searchReady = isSearchReady();
        if (!zeroDifference || !membersReady || !searchReady) {
            return CutoverStatus.syncing(membersReady, searchReady);
        }
        publishPermanentMarker();
        return lifecycleManaged.get() ? CutoverStatus.managed()
            : CutoverStatus.syncing(membersReady, searchReady);
    }
    
    private boolean allMembersSupportManagedLifecycle() {
        try {
            Collection<Member> members = serverMemberManager.allMembers();
            if (members == null || members.isEmpty()) {
                return false;
            }
            for (Member member : members) {
                if (member == null || !Boolean.TRUE.equals(
                    member.getExtendVal(
                        MemberMetaDataConstants.SUPPORT_MCP_LIFECYCLE_MANAGEMENT))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect cluster MCP lifecycle management capabilities", e);
            return false;
        }
    }
    
    private boolean isSearchReady() {
        try {
            boolean searchEnabled = Boolean.parseBoolean(
                EnvUtil.getProperty(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "true"));
            return !searchEnabled || searchReadinessService.isReady(
                AiResourceConstants.RESOURCE_TYPE_MCP, MCP_SEARCH_PROJECTION_VERSION);
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect MCP search projection readiness", e);
            return false;
        }
    }
    
    private void refreshMarkerIfNecessary(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now < nextMarkerRefreshAt) {
            return;
        }
        synchronized (this) {
            if (lifecycleManaged.get()) {
                return;
            }
            now = System.currentTimeMillis();
            if (!force && now < nextMarkerRefreshAt) {
                return;
            }
            nextMarkerRefreshAt = now + MARKER_REFRESH_INTERVAL_MILLIS;
            Marker marker = readMarker();
            if (marker != null) {
                lifecycleManaged.set(true);
                LOGGER.info("MCP management authority is LIFECYCLE_MANAGED");
            }
        }
    }
    
    private Marker readMarker() {
        try {
            ConfigQueryChainRequest request =
                ConfigQueryChainRequest.buildConfigQueryChainRequest(
                    MIGRATION_MARKER_DATA_ID, INTERNAL_GROUP,
                    com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response == null || response
                .getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
                return null;
            }
            if (!McpConfigUtils.isConfigFound(response.getStatus())
                || StringUtils.isBlank(response.getContent())) {
                LOGGER.warn("Ignored unavailable or empty MCP lifecycle management marker");
                return null;
            }
            Marker marker = JacksonUtils.toObj(response.getContent(), Marker.class);
            if (marker == null || marker.getSchemaVersion() != MARKER_SCHEMA_VERSION
                || !LIFECYCLE_MANAGED_STATE.equals(marker.getState())
                || marker.getCompletedAt() <= 0) {
                LOGGER.error("Ignored invalid MCP lifecycle management marker");
                return null;
            }
            return marker;
        } catch (Exception e) {
            LOGGER.warn("Failed to read MCP lifecycle management marker", e);
            return null;
        }
    }
    
    private void publishPermanentMarker() {
        Marker marker = new Marker();
        marker.setSchemaVersion(MARKER_SCHEMA_VERSION);
        marker.setState(LIFECYCLE_MANAGED_STATE);
        marker.setCompletedAt(System.currentTimeMillis());
        ConfigForm form = new ConfigForm();
        form.setNamespaceId(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
        form.setGroup(INTERNAL_GROUP);
        form.setDataId(MIGRATION_MARKER_DATA_ID);
        form.setContent(JacksonUtils.toJson(marker));
        form.setType(ConfigType.JSON.getType());
        form.setSrcUser("nacos");
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        requestInfo.setUpdateForExist(false);
        try {
            if (configOperationService.publishConfig(form, requestInfo, null)) {
                lifecycleManaged.set(true);
                LOGGER.info("Persisted permanent MCP LIFECYCLE_MANAGED marker");
            }
        } catch (ConfigAlreadyExistsException e) {
            refreshMarkerIfNecessary(true);
        } catch (Exception e) {
            LOGGER.warn("Failed to persist MCP lifecycle management marker; will retry", e);
        }
    }
    
    /**
     * Immutable cutover gate outcome used by reconciliation diagnostics.
     */
    public static final class CutoverStatus {
        
        private final boolean membersReady;
        
        private final boolean searchReady;
        
        private final boolean managed;
        
        private CutoverStatus(boolean membersReady, boolean searchReady, boolean managed) {
            this.membersReady = membersReady;
            this.searchReady = searchReady;
            this.managed = managed;
        }
        
        public static CutoverStatus syncing(boolean membersReady, boolean searchReady) {
            return new CutoverStatus(membersReady, searchReady, false);
        }
        
        public static CutoverStatus managed() {
            return new CutoverStatus(true, true, true);
        }
        
        public boolean isMembersReady() {
            return membersReady;
        }
        
        public boolean isSearchReady() {
            return searchReady;
        }
        
        public boolean isManaged() {
            return managed;
        }
    }
    
    /**
     * Serialized permanent marker.
     */
    public static class Marker {
        
        private int schemaVersion;
        
        private String state;
        
        private long completedAt;
        
        public int getSchemaVersion() {
            return schemaVersion;
        }
        
        public void setSchemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
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
    }
}
