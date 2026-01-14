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

package com.alibaba.nacos.api.ai.model.a2a;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.utils.StringUtils;

import java.util.Objects;

/**
 * Agent endpoint for A2A protocol.
 *
 * <p>
 *     Details split version of {@link AgentInterface}.
 * </p>
 *
 * @author xiweng.yy
 */
public class AgentEndpoint {
    
    /**
     * Same with {@link AgentInterface#transport}, Default `JSONRPC`.
     */
    private String transport = AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT;
    
    /**
     * Will be joined with {@link #port}, {@link #path}, {@link #protocol}. Such as `<a href="protocol://address:port/path?query">...</a>`
     */
    private String address;
    
    private int port;
    
    private String path = StringUtils.EMPTY;
    
    /**
     * If {@code true}, the target {@link AgentInterface} should be `https`, otherwise should be `http`. Default {@code false}.
     */
    private boolean supportTls;
    
    private String version;
    
    /**
     * Custom Protocol for A2A transport. Default `HTTP`.
     *
     * @since 3.1.1
     */
    private String protocol = AiConstants.A2a.A2A_ENDPOINT_DEFAULT_PROTOCOL;
    
    /**
     * Custom query for A2A url.
     *
     * @since 3.1.1
     */
    private String query;
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getTransport() {
        return transport;
    }
    
    public void setTransport(String transport) {
        this.transport = transport;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public boolean isSupportTls() {
        return supportTls;
    }
    
    public void setSupportTls(boolean supportTls) {
        this.supportTls = supportTls;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    /**
     * Only simple check address(IP or domain) and port.
     *
     * @param endpoint target endpoint
     * @return {@code true} if is equal, otherwise {@code false}
     */
    public boolean simpleEquals(AgentEndpoint endpoint) {
        return Objects.equals(address, endpoint.address) && Objects.equals(port, endpoint.port);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentEndpoint endpoint = (AgentEndpoint) o;
        return port == endpoint.port && supportTls == endpoint.supportTls && Objects.equals(transport,
                endpoint.transport) && Objects.equals(address, endpoint.address) && Objects.equals(path, endpoint.path)
                && Objects.equals(version, endpoint.version) && Objects.equals(protocol, endpoint.protocol)
                && Objects.equals(query, endpoint.query);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(transport, address, port, path, supportTls, version, protocol, query);
    }
    
    @Override
    public String toString() {
        return "AgentEndpoint{" + "transport='" + transport + '\'' + ", address='" + address + '\'' + ", port=" + port
                + ", path='" + path + '\'' + ", supportTls=" + supportTls + ", version='" + version + '\''
                + ", protocol='" + protocol + '\'' + ", query='" + query + '\'' + '}';
    }
}
