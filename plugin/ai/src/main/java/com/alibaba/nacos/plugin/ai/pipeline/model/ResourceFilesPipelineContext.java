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

import java.util.List;

/**
 * Publish pipeline context for AI resources represented as a set of files.
 *
 * @author nacos
 */
public class ResourceFilesPipelineContext extends PublishPipelineContext {
    
    @FunctionalInterface
    public interface FilesLoader {
        
        List<ResourceFileContent> load();
    }
    
    /**
     * Resource file content list loaded from storage.
     */
    private List<ResourceFileContent> files;
    
    private FilesLoader filesLoader;
    
    public List<ResourceFileContent> getFiles() {
        if (files == null && filesLoader != null) {
            files = filesLoader.load();
        }
        return files;
    }
    
    public void setFiles(List<ResourceFileContent> files) {
        this.files = files;
    }
    
    public FilesLoader getFilesLoader() {
        return filesLoader;
    }
    
    public void setFilesLoader(FilesLoader filesLoader) {
        this.filesLoader = filesLoader;
    }
}
