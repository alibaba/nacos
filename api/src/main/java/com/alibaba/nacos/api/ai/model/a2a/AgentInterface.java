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

import java.util.Objects;

/**
 * AgentInterface.
 *
 * @author KiteSoar
 */
public class AgentInterface {
    
    /**
     * For A2A 1.0.0.
     *
     * @since 3.2.1
     */
    private String url;
    
    /**
     * Legacy field for old A2A protocol compatibility, may be removed in future versions.
     * Use {@link #protocolBinding} for A2A 1.0.0.
     *
     * @deprecated For old A2A protocol compatibility only.
     */
    @Deprecated
    private String transport;
    
    /**
     * For A2A 1.0.0.
     *
     * @since 3.2.1
     */
    private String protocolBinding;
    
    /**
     * For A2A 1.0.0.
     *
     * @since 3.2.1
     */
    private String protocolVersion;
    
    /**
     * For A2A 1.0.0.
     *
     * @since 3.2.1
     */
    private String tenant;
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getTransport() {
        return transport;
    }
    
    public void setTransport(String transport) {
        this.transport = transport;
    }
    
    public String getProtocolBinding() {
        return protocolBinding;
    }
    
    public void setProtocolBinding(String protocolBinding) {
        this.protocolBinding = protocolBinding;
    }
    
    public String getProtocolVersion() {
        return protocolVersion;
    }
    
    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentInterface that = (AgentInterface) o;
        return Objects.equals(url, that.url) && Objects.equals(transport, that.transport)
            && Objects.equals(
                protocolBinding, that.protocolBinding)
            && Objects.equals(protocolVersion, that.protocolVersion)
            && Objects.equals(tenant, that.tenant);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(url, transport, protocolBinding, protocolVersion, tenant);
    }
}
