/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.api;

import com.alibaba.nacos.plugin.auth.constant.SignType;

/**
 * Request resources.
 *
 * @author xiweng.yy
 */
public class RequestResource {
    
    /**
     * Request type: naming or config.
     */
    private String type;
    
    private String namespace;
    
    private String group;
    
    /**
     * For type: naming, the resource should be service name.
     * For type: config, the resource should be config dataId.
     */
    private String resource;
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getResource() {
        return resource;
    }
    
    public void setResource(String resource) {
        this.resource = resource;
    }
    
    /**
     * Create new naming request resource builder.
     *
     * @return naming request resource builder
     */
    public static Builder namingBuilder() {
        Builder result = new Builder();
        result.setType(SignType.NAMING);
        return result;
    }
    
    /**
     * Create new config request resource builder.
     *
     * @return config request resource builder
     */
    public static Builder configBuilder() {
        Builder result = new Builder();
        result.setType(SignType.CONFIG);
        return result;
    }
    
    public static class Builder {
        
        private String type;
    
        private String namespace;
    
        private String group;
        
        private String resource;
    
        public void setType(String type) {
            this.type = type;
        }
    
        public Builder setNamespace(String namespace) {
            this.namespace = namespace;
            return this;
        }
    
        public Builder setGroup(String group) {
            this.group = group;
            return this;
        }
    
        public Builder setResource(String resource) {
            this.resource = resource;
            return this;
        }
    
        /**
         * Build request resource.
         *
         * @return request resource
         */
        public RequestResource build() {
            RequestResource result = new RequestResource();
            result.setType(type);
            result.setNamespace(namespace);
            result.setGroup(group);
            result.setResource(resource);
            return result;
        }
    }
}
