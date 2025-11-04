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

package com.alibaba.nacos.api.naming.remote.request;

import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.Naming.NAMING_MODULE;

/**
 * fuzzy watch sync request from Nacos server.
 *
 * @author shiyiyue
 */
public class NamingFuzzyWatchSyncRequest extends AbstractFuzzyWatchNotifyRequest {
    
    /**
     * The pattern used to match service keys for the services.
     */
    private String groupKeyPattern;
    
    /**
     * The set of contexts containing information about the service.
     */
    private Set<Context> contexts;
    
    private int totalBatch;
    
    private int currentBatch;
    
    public NamingFuzzyWatchSyncRequest() {
    
    }
    
    public NamingFuzzyWatchSyncRequest(String pattern, String syncType, Set<Context> contexts) {
        super(syncType);
        this.groupKeyPattern = pattern;
        this.contexts = contexts;
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
     * Build SyncNotifyRequest.
     *
     * @param pattern      pattern
     * @param syncType     syncType
     * @param contexts     contexts
     * @param totalBatch   totalBatch
     * @param currentBatch currentBatch
     * @return A new NamingFuzzyWatchSyncRequest instance
     */
    public static NamingFuzzyWatchSyncRequest buildSyncNotifyRequest(String pattern, String syncType,
            Set<Context> contexts, int totalBatch, int currentBatch) {
        NamingFuzzyWatchSyncRequest namingFuzzyWatchSyncRequest = new NamingFuzzyWatchSyncRequest(pattern, syncType,
                contexts);
        namingFuzzyWatchSyncRequest.currentBatch = currentBatch;
        namingFuzzyWatchSyncRequest.totalBatch = totalBatch;
        return namingFuzzyWatchSyncRequest;
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
    
    @Override
    public String getModule() {
        return NAMING_MODULE;
    }
    
    /**
     * fuzzy watch sync context.
     */
    public static class Context {
        
        /**
         * service key.
         */
        String serviceKey;
        
        /**
         * changed type.
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
         * @param serviceKey  The groupKey associated of the configuration.
         * @param changedType The type of the configuration change event.
         * @return A new context object initialized with the provided parameters.
         */
        public static NamingFuzzyWatchSyncRequest.Context build(String serviceKey, String changedType) {
            NamingFuzzyWatchSyncRequest.Context context = new NamingFuzzyWatchSyncRequest.Context();
            context.setServiceKey(serviceKey);
            context.setChangedType(changedType);
            return context;
        }
        
        public String getServiceKey() {
            return serviceKey;
        }
        
        public void setServiceKey(String serviceKey) {
            this.serviceKey = serviceKey;
        }
        
        public String getChangedType() {
            return changedType;
        }
        
        public void setChangedType(String changedType) {
            this.changedType = changedType;
        }
    }
}
