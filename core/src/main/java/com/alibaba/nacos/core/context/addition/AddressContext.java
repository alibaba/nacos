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

package com.alibaba.nacos.core.context.addition;

/**
 * Nacos request address information context.
 *
 * @author xiweng.yy
 */
public class AddressContext {
    
    /**
     * Request source ip, it's the ip of the client, most situations are same with remoteIp.
     */
    private String sourceIp;
    
    /**
     * Request source port, it's the port of the client,  most situations are same with remoteIp.
     */
    private int sourcePort;
    
    /**
     * Request connection ip, it should be got from the socket, which means the ip seen by nacos server.
     */
    private String remoteIp;
    
    /**
     * Request connection port, it should be got from the socket, which means the port seen by nacos server.
     */
    private int remotePort;
    
    /**
     * Request host, it's the host of the client, nullable when can't get it from request or connection.
     */
    private String host;
    
    public String getSourceIp() {
        return sourceIp;
    }
    
    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }
    
    public int getSourcePort() {
        return sourcePort;
    }
    
    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }
    
    public String getRemoteIp() {
        return remoteIp;
    }
    
    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }
    
    public int getRemotePort() {
        return remotePort;
    }
    
    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
}
