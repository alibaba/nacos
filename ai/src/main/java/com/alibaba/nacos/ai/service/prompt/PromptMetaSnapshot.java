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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;

/**
 * Prompt meta snapshot.
 *
 * @author nacos
 */
public class PromptMetaSnapshot {
    
    private final PromptMetaInfo meta;
    
    private final String md5;
    
    public PromptMetaSnapshot(PromptMetaInfo meta, String md5) {
        this.meta = meta;
        this.md5 = md5;
    }
    
    public PromptMetaInfo getMeta() {
        return meta;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public static PromptMetaSnapshot empty() {
        return new PromptMetaSnapshot(null, null);
    }
}
