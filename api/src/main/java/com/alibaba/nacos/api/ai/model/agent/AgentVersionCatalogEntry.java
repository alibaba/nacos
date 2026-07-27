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

package com.alibaba.nacos.api.ai.model.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

/**
 * One online Agent version in the compact version catalog.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentVersionCatalogEntry implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String version;
    
    private List<String> labels;
    
    private List<String> protocols;
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public List<String> getLabels() {
        return labels;
    }
    
    public void setLabels(List<String> labels) {
        this.labels = labels;
    }
    
    public List<String> getProtocols() {
        return protocols;
    }
    
    public void setProtocols(List<String> protocols) {
        this.protocols = protocols;
    }
}
