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
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.utils.StringUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a request for batch fuzzy listening configurations.
 *
 * <p>This request is used to request batch fuzzy listening configurations from the server. It contains a set of
 * contexts, each representing a fuzzy listening context.
 *
 * @author stone-98
 * @date 2024/3/4
 */
public class ConfigBatchFuzzyListenRequest extends Request {
    
    /**
     * Set of fuzzy listening contexts.
     */
    private Set<Context> contexts = new HashSet<>();
    
    /**
     * Constructs an empty ConfigBatchFuzzyListenRequest.
     */
    public ConfigBatchFuzzyListenRequest() {
    }
    
    /**
     * Adds a new context to the request.
     *
     * @param tenant         The namespace or tenant associated with the configurations
     * @param group          The group associated with the configurations
     * @param dataIdPattern  The pattern for matching data IDs
     * @param dataIds        Set of data IDs
     * @param listen         Flag indicating whether to listen for changes
     * @param isInitializing Flag indicating whether the client is initializing
     */
    public void addContext(String tenant, String group, String dataIdPattern, Set<String> dataIds, boolean listen,
            boolean isInitializing) {
        contexts.add(
                new Context(StringUtils.isEmpty(tenant) ? Constants.DEFAULT_NAMESPACE_ID : tenant, group, dataIdPattern,
                        dataIds, listen, isInitializing));
    }
    
    /**
     * Get the set of fuzzy listening contexts.
     *
     * @return The set of contexts
     */
    public Set<Context> getContexts() {
        return contexts;
    }
    
    /**
     * Set the set of fuzzy listening contexts.
     *
     * @param contexts The set of contexts to be set
     */
    public void setContexts(Set<Context> contexts) {
        this.contexts = contexts;
    }
    
    /**
     * Get the module name for this request.
     *
     * @return The module name
     */
    @Override
    public String getModule() {
        return Constants.Config.CONFIG_MODULE;
    }
    
    /**
     * Represents a fuzzy listening context.
     */
    public static class Context {
        
        /**
         * The namespace or tenant associated with the configurations.
         */
        private String tenant;
        
        /**
         * The group associated with the configurations.
         */
        private String group;
        
        /**
         * The pattern for matching data IDs.
         */
        private String dataIdPattern;
        
        /**
         * Set of data IDs.
         */
        private Set<String> dataIds;
        
        /**
         * Flag indicating whether to listen for changes.
         */
        private boolean listen;
        
        /**
         * Flag indicating whether the client is initializing.
         */
        private boolean isInitializing;
        
        /**
         * Constructs an empty Context.
         */
        public Context() {
        }
        
        /**
         * Constructs a Context with the specified parameters.
         *
         * @param tenant         The namespace or tenant associated with the configurations
         * @param group          The group associated with the configurations
         * @param dataIdPattern  The pattern for matching data IDs
         * @param dataIds        Set of data IDs
         * @param listen         Flag indicating whether to listen for changes
         * @param isInitializing Flag indicating whether the client is initializing
         */
        public Context(String tenant, String group, String dataIdPattern, Set<String> dataIds, boolean listen,
                boolean isInitializing) {
            this.tenant = tenant;
            this.group = group;
            this.dataIdPattern = dataIdPattern;
            this.dataIds = dataIds;
            this.listen = listen;
            this.isInitializing = isInitializing;
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
        
        public String getDataIdPattern() {
            return dataIdPattern;
        }
        
        public void setDataIdPattern(String dataIdPattern) {
            this.dataIdPattern = dataIdPattern;
        }
        
        public Set<String> getDataIds() {
            return dataIds;
        }
        
        public void setDataIds(Set<String> dataIds) {
            this.dataIds = dataIds;
        }
        
        public boolean isListen() {
            return listen;
        }
        
        public void setListen(boolean listen) {
            this.listen = listen;
        }
        
        public boolean isInitializing() {
            return isInitializing;
        }
        
        public void setInitializing(boolean initializing) {
            isInitializing = initializing;
        }
        
        /**
         * Indicates whether some other object is "equal to" this one.
         *
         * @param o The reference object with which to compare
         * @return True if this object is the same as the obj argument, false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Context that = (Context) o;
            return Objects.equals(tenant, that.tenant) && Objects.equals(group, that.group) && Objects.equals(
                    dataIdPattern, that.dataIdPattern) && Objects.equals(dataIds, that.dataIds) && Objects.equals(
                    listen, that.listen) && Objects.equals(isInitializing, that.isInitializing);
        }
        
        /**
         * Returns a hash code value for the object.
         *
         * @return A hash code value for this object
         */
        @Override
        public int hashCode() {
            return Objects.hash(tenant, group, dataIdPattern, dataIds, listen, isInitializing);
        }
    }
}
