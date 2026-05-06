/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.tls;

import com.alibaba.nacos.common.remote.TlsConfig;

/**
 * Represents the TLS configuration for an RPC server.
 * This class extends TlsConfig to inherit common TLS configuration properties.
 *
 * @author githubcheng2978.
 */
public class RpcServerTlsConfig extends TlsConfig {

    /**
     *  The class representing the configuration for SSL context refreshing in the RPC server.
     */
    private String sslContextRefresher = "";

    /**
     * Indicates whether compatibility mode is enabled.
     */
    private Boolean compatibility = true;

    /**
     * Gets the compatibility mode status.
     *
     * @return true if compatibility mode is enabled, false otherwise.
     */
    public Boolean getCompatibility() {
        return compatibility;
    }

    /**
     * Sets the compatibility mode status.
     *
     * @param compatibility true to enable compatibility mode, false otherwise.
     */
    public void setCompatibility(Boolean compatibility) {
        this.compatibility = compatibility;
    }

    /**
     * Gets the SSL context refresher.
     *
     * @return the SSL context refresher.
     */
    public String getSslContextRefresher() {
        return sslContextRefresher;
    }

    /**
     * Sets the SSL context refresher.
     *
     * @param sslContextRefresher the SSL context refresher to set.
     */
    public void setSslContextRefresher(String sslContextRefresher) {
        this.sslContextRefresher = sslContextRefresher;
    }
}
