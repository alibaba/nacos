/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.ai.pipeline.model;

/**
 * Publish pipeline context base class, containing common fields shared by all resource types.
 *
 * <p>Different resource types extend this base class with their own specific fields.
 * Pipeline plugins can cast the base class to the corresponding subclass based on
 * {@link #getResourceType()} in their {@code execute()} method.</p>
 *
 * @author mosong.lp
 * @since 3.2.0
 */
public class PublishPipelineContext {
    
    /**
     * Resource type (SKILL / PROMPT / OTHERS).
     */
    private PublishPipelineResourceType resourceType;
    
    /**
     * Resource name, e.g. "nacos-skill-registry".
     */
    private String resourceName;
    
    /**
     * Namespace ID.
     */
    private String namespaceId;
    
    /**
     * Current version under review, e.g. "v4".
     */
    private String version;
    
    public PublishPipelineContext() {
    }
    
    public PublishPipelineResourceType getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(PublishPipelineResourceType resourceType) {
        this.resourceType = resourceType;
    }
    
    public String getResourceName() {
        return resourceName;
    }
    
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
}
