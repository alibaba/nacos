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

package com.alibaba.nacos.client.ai.remote.redo;

import java.util.Objects;

/**
 * Nacos AI module mcp server endpoint required information for redo data.
 *
 * @author xiweng.yy
 */
public class McpServerEndpoint {
    
    private final String address;
    
    private final int port;
    
    private final String version;
    
    public McpServerEndpoint(String address, int port, String version) {
        this.address = address;
        this.port = port;
        this.version = version;
    }
    
    public String getAddress() {
        return address;
    }
    
    public int getPort() {
        return port;
    }
    
    public String getVersion() {
        return version;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        McpServerEndpoint that = (McpServerEndpoint) o;
        return port == that.port && Objects.equals(address, that.address) && Objects.equals(version, that.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(address, port, version);
    }
}
