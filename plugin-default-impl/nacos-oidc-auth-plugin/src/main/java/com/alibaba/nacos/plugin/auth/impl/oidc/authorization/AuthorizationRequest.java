/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oidc.authorization;

/**
 * Authorization request model for IdP authorization endpoint.
 *
 * @author WangzJi
 */
public class AuthorizationRequest {
    
    /**
     * User's access token.
     */
    private String token;
    
    /**
     * Resource identifier (e.g., "nacos:config:dev:app.yaml").
     */
    private String resource;
    
    /**
     * Action to perform (e.g., "read", "write").
     */
    private String action;
    
    /**
     * Resource type (e.g., "config", "naming").
     */
    private String resourceType;
    
    /**
     * Namespace ID.
     */
    private String namespace;
    
    /**
     * Group name.
     */
    private String group;
    
    /**
     * Resource name (e.g., dataId for config).
     */
    private String resourceName;
    
    public AuthorizationRequest() {
    }
    
    public AuthorizationRequest(String token, String resource, String action) {
        this.token = token;
        this.resource = resource;
        this.action = action;
    }
    
    /**
     * Build resource URI from components.
     *
     * @return resource URI in format "nacos:{type}:{namespace}:{name}"
     */
    public String buildResourceUri() {
        if (resource != null) {
            return resource;
        }
        StringBuilder uri = new StringBuilder("nacos");
        if (resourceType != null) {
            uri.append(":").append(resourceType);
        }
        if (namespace != null) {
            uri.append(":").append(namespace);
        }
        if (group != null) {
            uri.append(":").append(group);
        }
        if (resourceName != null) {
            uri.append(":").append(resourceName);
        }
        return uri.toString();
    }
    
    /**
     * Convert to JSON string for HTTP request body.
     *
     * @return JSON string
     */
    public String toJson() {
        StringBuilder json = new StringBuilder("{");
        json.append("\"token\":\"").append(escapeJson(token)).append("\"");
        json.append(",\"resource\":\"").append(escapeJson(buildResourceUri())).append("\"");
        json.append(",\"action\":\"").append(escapeJson(action)).append("\"");
        if (resourceType != null) {
            json.append(",\"resourceType\":\"").append(escapeJson(resourceType)).append("\"");
        }
        if (namespace != null) {
            json.append(",\"namespace\":\"").append(escapeJson(namespace)).append("\"");
        }
        if (group != null) {
            json.append(",\"group\":\"").append(escapeJson(group)).append("\"");
        }
        if (resourceName != null) {
            json.append(",\"resourceName\":\"").append(escapeJson(resourceName)).append("\"");
        }
        json.append("}");
        return json.toString();
    }
    
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    // Getters and Setters
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getResource() {
        return resource;
    }
    
    public void setResource(String resource) {
        this.resource = resource;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
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
    
    public String getResourceName() {
        return resourceName;
    }
    
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
    
    /**
     * Builder for AuthorizationRequest.
     */
    public static class Builder {
        
        private final AuthorizationRequest request = new AuthorizationRequest();
        
        public Builder token(String token) {
            request.setToken(token);
            return this;
        }
        
        public Builder resource(String resource) {
            request.setResource(resource);
            return this;
        }
        
        public Builder action(String action) {
            request.setAction(action);
            return this;
        }
        
        public Builder resourceType(String resourceType) {
            request.setResourceType(resourceType);
            return this;
        }
        
        public Builder namespace(String namespace) {
            request.setNamespace(namespace);
            return this;
        }
        
        public Builder group(String group) {
            request.setGroup(group);
            return this;
        }
        
        public Builder resourceName(String resourceName) {
            request.setResourceName(resourceName);
            return this;
        }
        
        public AuthorizationRequest build() {
            return request;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}
