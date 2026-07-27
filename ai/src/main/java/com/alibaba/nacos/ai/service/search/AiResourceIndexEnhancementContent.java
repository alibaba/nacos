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

package com.alibaba.nacos.ai.service.search;

/**
 * Source content snippet used as input for AI resource index enhancement.
 *
 * @author nacos
 */
public class AiResourceIndexEnhancementContent {
    
    private final String path;
    
    private final String text;
    
    public AiResourceIndexEnhancementContent(String path, String text) {
        this.path = path;
        this.text = text;
    }
    
    public String getPath() {
        return path;
    }
    
    public String getText() {
        return text;
    }
}
