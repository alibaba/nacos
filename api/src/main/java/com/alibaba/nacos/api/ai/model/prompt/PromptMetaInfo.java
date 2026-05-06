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

package com.alibaba.nacos.api.ai.model.prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt meta information.
 *
 * @author nacos
 */
public class PromptMetaInfo extends PromptMetaSummary {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Version string list for backward compatibility with legacy clients.
     */
    private List<String> versions = new ArrayList<>();
    
    /**
     * Detailed version summaries including status, author, etc.
     */
    private List<PromptVersionSummary> versionDetails = new ArrayList<>();
    
    public List<String> getVersions() {
        return versions;
    }
    
    public void setVersions(List<String> versions) {
        this.versions = versions;
    }
    
    public List<PromptVersionSummary> getVersionDetails() {
        return versionDetails;
    }
    
    public void setVersionDetails(List<PromptVersionSummary> versionDetails) {
        this.versionDetails = versionDetails;
    }
}
