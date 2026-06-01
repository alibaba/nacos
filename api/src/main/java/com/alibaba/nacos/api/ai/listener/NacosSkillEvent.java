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

package com.alibaba.nacos.api.ai.listener;

/**
 * Nacos AI module skill event.
 *
 * <p>Triggered when a subscribed skill changes on the server side. The {@link #zipBytes}
 * payload carries the freshly downloaded skill ZIP archive (SKILL.md plus resources); the
 * {@link #md5} field is the server-published content fingerprint suitable for diffing
 * subsequent revisions or persisting alongside the local cache.
 *
 * @author nacos
 * @since 3.2.0
 */
public class NacosSkillEvent implements NacosAiEvent {
    
    private final String skillName;
    
    private final byte[] zipBytes;
    
    private final String md5;
    
    private final String resolvedVersion;
    
    public NacosSkillEvent(String skillName, byte[] zipBytes, String md5, String resolvedVersion) {
        this.skillName = skillName;
        this.zipBytes = zipBytes;
        this.md5 = md5;
        this.resolvedVersion = resolvedVersion;
    }
    
    /**
     * Get the skill name.
     *
     * @return skill name
     */
    public String getSkillName() {
        return skillName;
    }
    
    /**
     * Get the skill ZIP payload, may be {@code null} when the skill has been deleted on the server.
     *
     * @return skill ZIP byte array, or {@code null} if the skill no longer exists
     */
    public byte[] getZipBytes() {
        return zipBytes;
    }
    
    /**
     * Get the published content MD5 of this skill revision, may be {@code null} for delete events
     * or when the server response did not carry the fingerprint header.
     *
     * @return content MD5
     */
    public String getMd5() {
        return md5;
    }
    
    /**
     * Get the resolved version string when the listener was registered against a label, may be
     * {@code null} when the request used an explicit version or the response did not carry the
     * resolved version header.
     *
     * @return resolved version, optional
     */
    public String getResolvedVersion() {
        return resolvedVersion;
    }
}
