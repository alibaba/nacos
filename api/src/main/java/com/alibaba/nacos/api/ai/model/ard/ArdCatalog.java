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

package com.alibaba.nacos.api.ai.model.ard;

import java.util.ArrayList;
import java.util.List;

/**
 * ARD ai-catalog.json manifest.
 *
 * @author nacos
 */
public class ArdCatalog {
    
    private String specVersion;
    
    private ArdHostInfo host;
    
    private List<ArdSearchResult> entries = new ArrayList<>();
    
    public String getSpecVersion() {
        return specVersion;
    }
    
    public void setSpecVersion(String specVersion) {
        this.specVersion = specVersion;
    }
    
    public ArdHostInfo getHost() {
        return host;
    }
    
    public void setHost(ArdHostInfo host) {
        this.host = host;
    }
    
    public List<ArdSearchResult> getEntries() {
        return entries;
    }
    
    public void setEntries(List<ArdSearchResult> entries) {
        this.entries = entries;
    }
}
