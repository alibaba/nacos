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
 * Resource file content, representing a single file in storage.
 *
 * @author mosong.lp
 * @since 3.2.0
 */
public class ResourceFileContent {
    
    /**
     * File path, e.g. "templates/config_check.json", "SKILL.md".
     */
    private String filePath;
    
    /**
     * File content (text).
     */
    private String content;
    
    public ResourceFileContent() {
    }
    
    public ResourceFileContent(String filePath, String content) {
        this.filePath = filePath;
        this.content = content;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    @Override
    public String toString() {
        return "ResourceFileContent{filePath='" + filePath + "'}";
    }
}
