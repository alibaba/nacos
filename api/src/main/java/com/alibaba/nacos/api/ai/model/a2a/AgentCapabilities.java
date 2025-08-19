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
 * AgentCapabilities.
 *
 * @author KiteSoar
 */
public class AgentCapabilities {
    
    private Boolean streaming;
    
    private Boolean pushNotifications;
    
    private Boolean stateTransitionHistory;
    
    private List<AgentExtension> extensions;
    
    public Boolean getStreaming() {
        return streaming;
    }
    
    public void setStreaming(Boolean streaming) {
        this.streaming = streaming;
    }
    
    public Boolean getPushNotifications() {
        return pushNotifications;
    }
    
    public void setPushNotifications(Boolean pushNotifications) {
        this.pushNotifications = pushNotifications;
    }
    
    public Boolean getStateTransitionHistory() {
        return stateTransitionHistory;
    }
    
    public void setStateTransitionHistory(Boolean stateTransitionHistory) {
        this.stateTransitionHistory = stateTransitionHistory;
    }
    
    public List<AgentExtension> getExtensions() {
        return extensions;
    }
    
    public void setExtensions(List<AgentExtension> extensions) {
        this.extensions = extensions;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentCapabilities that = (AgentCapabilities) o;
        return Objects.equals(streaming, that.streaming) && Objects.equals(pushNotifications, that.pushNotifications)
                && Objects.equals(stateTransitionHistory, that.stateTransitionHistory) && Objects.equals(extensions,
                that.extensions);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(streaming, pushNotifications, stateTransitionHistory, extensions);
    }
}
