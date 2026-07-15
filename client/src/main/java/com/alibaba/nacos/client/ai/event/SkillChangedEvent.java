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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.common.notify.Event;

/**
 * Skill changed event for internal notification.
 *
 * <p>Published by {@code NacosSkillCacheHolder} whenever the polling loop detects that the
 * server-side published skill content has changed (i.e. the response MD5 is different from the
 * locally cached MD5). {@code AiChangeNotifier} consumes the event and dispatches it to all
 * registered {@code AbstractNacosSkillListener}s for the same cache key.
 *
 * @author nacos
 */
public class SkillChangedEvent extends Event {
    
    private static final long serialVersionUID = 1L;
    
    private final String skillName;
    
    private final String cacheKey;
    
    private final byte[] zipBytes;
    
    private final String md5;
    
    private final String resolvedVersion;
    
    public SkillChangedEvent(String skillName, String cacheKey, byte[] zipBytes, String md5,
        String resolvedVersion) {
        this.skillName = skillName;
        this.cacheKey = cacheKey;
        this.zipBytes = zipBytes;
        this.md5 = md5;
        this.resolvedVersion = resolvedVersion;
    }
    
    public String getSkillName() {
        return skillName;
    }
    
    public String getCacheKey() {
        return cacheKey;
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
