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
 *
 */

package com.alibaba.nacos.api.ai.model.a2a;

import java.util.List;
import java.util.Objects;

/**
 * AgentCardVersionInfo.
 *
 * @author KiteSoar
 */
public class AgentCardVersionInfo extends AgentCardBasicInfo {
    
    private String latestPublishedVersion;
    
    private List<AgentVersionDetail> versionDetails;
    
    private String registrationType;
    
    public String getLatestPublishedVersion() {
        return latestPublishedVersion;
    }
    
    public void setLatestPublishedVersion(String latestPublishedVersion) {
        this.latestPublishedVersion = latestPublishedVersion;
    }
    
    public List<AgentVersionDetail> getVersionDetails() {
        return versionDetails;
    }
    
    public void setVersionDetails(List<AgentVersionDetail> versionDetails) {
        this.versionDetails = versionDetails;
    }
    
    public String getRegistrationType() {
        return registrationType;
    }
    
    public void setRegistrationType(String registrationType) {
        this.registrationType = registrationType;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AgentCardVersionInfo that = (AgentCardVersionInfo) o;
        return Objects.equals(latestPublishedVersion, that.latestPublishedVersion) && Objects.equals(versionDetails,
                that.versionDetails) && Objects.equals(registrationType, that.registrationType);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), latestPublishedVersion, versionDetails, registrationType);
    }
}
