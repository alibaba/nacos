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

package com.alibaba.nacos.client.ai.remote;

/**
 * Response wrapper for the listener-style skill query. Carries the freshly downloaded ZIP bytes
 * together with the listener-related response headers ({@code X-Nacos-Skill-Md5} and
 * {@code X-Nacos-Skill-Resolved-Version}) so the client cache can short-circuit the next poll
 * when the published content has not changed.
 *
 * @author nacos
 * @since 3.2.0
 */
public class SkillQueryResponse {
    
    private final byte[] zipBytes;
    
    private final String md5;
    
    private final String resolvedVersion;
    
    public SkillQueryResponse(byte[] zipBytes, String md5, String resolvedVersion) {
        this.zipBytes = zipBytes;
        this.md5 = md5;
        this.resolvedVersion = resolvedVersion;
    }
    
    public byte[] getZipBytes() {
        return zipBytes;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public String getResolvedVersion() {
        return resolvedVersion;
    }
}
