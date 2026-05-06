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
public class ConfigFuzzyWatchSyncRequest extends AbstractFuzzyWatchNotifyRequest {
    
    /**
     * The pattern used to match group keys for the configurations.
     */
    private String groupKeyPattern;
    
    /**
     * The set of contexts containing information about the configurations.
     */
    private Set<Context> contexts;
    
    /**
     * see FUZZY_WATCH_INIT_NOTIFY,FINISH_FUZZY_WATCH_INIT_NOTIFY,FUZZY_WATCH_DIFF_SYNC_NOTIFY.
     */
    private String syncType;
    
    private int totalBatch;
    
    private int currentBatch;
    
    public String getSyncType() {
        return syncType;
    }
    
    public void setSyncType(String syncType) {
        this.syncType = syncType;
    }
    
    public int getTotalBatch() {
        return totalBatch;
    }
    
    public void setTotalBatch(int totalBatch) {
        this.totalBatch = totalBatch;
    }
    
    public int getCurrentBatch() {
        return currentBatch;
    }
    
    public void setCurrentBatch(int currentBatch) {
        this.currentBatch = currentBatch;
    }
    
    /**
     * Constructs an empty FuzzyListenNotifyDiffRequest.
     */
    public ConfigFuzzyWatchSyncRequest() {
    }
    
    /**
     * Constructs a FuzzyListenNotifyDiffRequest with the specified parameters.
     *
     * @param groupKeyPattern The pattern used to match group keys for the configurations
     * @param contexts        The set of contexts containing information about the configurations
     */
    private ConfigFuzzyWatchSyncRequest(String syncType, String groupKeyPattern, Set<Context> contexts, int totalBatch,
            int currentBatch) {
        this.groupKeyPattern = groupKeyPattern;
        this.contexts = contexts;
        this.syncType = syncType;
        this.currentBatch = currentBatch;
        this.totalBatch = totalBatch;
        
    }
    
    /**
     * Builds an initial FuzzyListenNotifyDiffRequest with the specified set of contexts and group key pattern.
     *
     * @param contexts        The set of contexts containing information about the configurations
     * @param groupKeyPattern The pattern used to match group keys for the configurations
     * @return An initial FuzzyListenNotifyDiffRequest
     */
    public static ConfigFuzzyWatchSyncRequest buildSyncRequest(String syncType, Set<Context> contexts,
            String groupKeyPattern, int totalBatch, int currentBatch) {
        return new ConfigFuzzyWatchSyncRequest(syncType, groupKeyPattern, contexts, totalBatch, currentBatch);
    }
    
    /**
     * Builds fuzzy watch init finish request.
     *
     * @param groupKeyPattern The pattern used to match group keys for the configurations
     * @return A final FuzzyListenNotifyDiffRequest
     */
    public static ConfigFuzzyWatchSyncRequest buildInitFinishRequest(String groupKeyPattern) {
        return new ConfigFuzzyWatchSyncRequest(Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY, groupKeyPattern, null, 0, 0);
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
        
        String groupKey;
        
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
         * @param groupKey    The groupKey associated of the configuration.
         * @param changedType The type of the configuration change event.
         * @return A new context object initialized with the provided parameters.
         */
        public static Context build(String groupKey, String changedType) {
            Context context = new Context();
            context.setGroupKey(groupKey);
            context.setChangedType(changedType);
            return context;
        }
        
        public String getGroupKey() {
            return groupKey;
        }
        
        public void setGroupKey(String groupKey) {
            this.groupKey = groupKey;
        }
        
        public String getChangedType() {
            return changedType;
        }
        
        public void setChangedType(String changedType) {
            this.changedType = changedType;
        }
    }
    
}
