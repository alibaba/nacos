/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config.remote.request;

import com.alibaba.nacos.api.common.Constants;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a request to notify the difference between client and server side.
 *
 * <p>This request is used to notify clients about the difference in configurations that match fuzzy listening
 * patterns.
 *
 * @author stone-98
 * @date 2024/3/6
 */
public class FuzzyWatchDiffSyncRequest extends AbstractFuzzyWatchNotifyRequest {
    
    /**
     * The pattern used to match group keys for the configurations.
     */
    private String groupKeyPattern;
    
    /**
     * The set of contexts containing information about the configurations.
     */
    private Set<Context> contexts;
    
    /**
     *see FUZZY_WATCH_INIT_NOTIFY,FINISH_FUZZY_WATCH_INIT_NOTIFY,FUZZY_WATCH_DIFF_SYNC_NOTIFY
     */
    private String syncType;
    
    public String getSyncType() {
        return syncType;
    }
    
    public void setSyncType(String syncType) {
        this.syncType = syncType;
    }
    
    /**
     * Constructs an empty FuzzyListenNotifyDiffRequest.
     */
    public FuzzyWatchDiffSyncRequest() {
    }
    
    /**
     * Constructs a FuzzyListenNotifyDiffRequest with the specified parameters.
     *
     * @param groupKeyPattern The pattern used to match group keys for the configurations
     * @param contexts        The set of contexts containing information about the configurations
     */
    public FuzzyWatchDiffSyncRequest(String syncType, String groupKeyPattern, Set<Context> contexts) {
        this.groupKeyPattern = groupKeyPattern;
        this.contexts = contexts;
        this.syncType = syncType;
        
    }
    
    /**
     * Builds an initial FuzzyListenNotifyDiffRequest with the specified set of contexts and group key pattern.
     *
     * @param contexts        The set of contexts containing information about the configurations
     * @param groupKeyPattern The pattern used to match group keys for the configurations
     * @return An initial FuzzyListenNotifyDiffRequest
     */
    public static FuzzyWatchDiffSyncRequest buildInitRequest(Set<Context> contexts, String groupKeyPattern) {
        return new FuzzyWatchDiffSyncRequest(Constants.FUZZY_WATCH_INIT_NOTIFY, groupKeyPattern, contexts);
    }
    
    /**
     * Builds an initial FuzzyListenNotifyDiffRequest with the specified set of contexts and group key pattern.
     *
     * @param contexts        The set of contexts containing information about the configurations
     * @param groupKeyPattern The pattern used to match group keys for the configurations
     * @return An initial FuzzyListenNotifyDiffRequest
     */
    public static FuzzyWatchDiffSyncRequest buildDiffSyncRequest(Set<Context> contexts, String groupKeyPattern) {
        return new FuzzyWatchDiffSyncRequest(Constants.FUZZY_WATCH_DIFF_SYNC_NOTIFY, groupKeyPattern, contexts);
    }
    
    /**
     * Builds a final FuzzyListenNotifyDiffRequest with the specified group key pattern.
     *
     * @param groupKeyPattern The pattern used to match group keys for the configurations
     * @return A final FuzzyListenNotifyDiffRequest
     */
    public static FuzzyWatchDiffSyncRequest buildInitFinishRequest(String groupKeyPattern) {
        return new FuzzyWatchDiffSyncRequest(Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY, groupKeyPattern,
                new HashSet<>());
    }
    
    public String getGroupKeyPattern() {
        return groupKeyPattern;
    }
    
    public void setGroupKeyPattern(String groupKeyPattern) {
        this.groupKeyPattern = groupKeyPattern;
    }
    
    public Set<Context> getContexts() {
        return contexts;
    }
    
    public void setContexts(Set<Context> contexts) {
        this.contexts = contexts;
    }
    
    /**
     * Represents context information about a configuration.
     */
    public static class Context {
        
        private String tenant;
        
        private String group;
        
        private String dataId;
        
        /**
         * see {@link com.alibaba.nacos.api.common.Constants.ConfigChangedType ADD_CONFIG&} ADD_CONFIG: a new config
         * should be added for  clientside . DELETE_CONFIG: a  config should be removed for  clientside .
         */
        private String changedType;
        
        /**
         * Constructs an empty Context object.
         */
        public Context() {
        }
        
        /**
         * Builds a new context object with the provided parameters.
         *
         * @param tenant      The tenant associated with the configuration.
         * @param group       The group associated with the configuration.
         * @param dataId      The data ID of the configuration.
         * @param changedType The type of the configuration change event.
         * @return A new context object initialized with the provided parameters.
         */
        public static Context build(String tenant, String group, String dataId, String changedType) {
            Context context = new Context();
            context.setTenant(tenant);
            context.setGroup(group);
            context.setDataId(dataId);
            context.setChangedType(changedType);
            return context;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(tenant, group, dataId, tenant);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Context that = (Context) o;
            return Objects.equals(tenant, that.tenant) && Objects.equals(group, that.group) && Objects.equals(dataId,
                    that.dataId) && Objects.equals(changedType, that.changedType);
        }
        
        public String getTenant() {
            return tenant;
        }
        
        public void setTenant(String tenant) {
            this.tenant = tenant;
        }
        
        public String getGroup() {
            return group;
        }
        
        public void setGroup(String group) {
            this.group = group;
        }
        
        public String getDataId() {
            return dataId;
        }
        
        public void setDataId(String dataId) {
            this.dataId = dataId;
        }
        
        public String getChangedType() {
            return changedType;
        }
        
        public void setChangedType(String changedType) {
            this.changedType = changedType;
        }
    }
    
}
