/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote.request;

/**
 * request to setup a connection.
 *
 * @author liuzunfei
 * @version $Id: ConnectionSetupRequest.java, v 0.1 2020年08月06日 2:42 PM liuzunfei Exp $
 */
public class ConnectionSetupRequest extends InternalRequest {
    
    private String connectionId;
    
    private String clientIp;
    
    private String clientVersion;
    
    public ConnectionSetupRequest(String connectionId, String clientIp, String clientVersion) {
        this.clientIp = clientIp;
        this.connectionId = connectionId;
        this.clientVersion = clientVersion;
    }
    
    @Override
    public String getType() {
        return RequestTypeConstants.CONNECTION_SETUP;
    }
    
    /**
     * Getter method for property <tt>connectionId</tt>.
     *
     * @return property value of connectionId
     */
    public String getConnectionId() {
        return connectionId;
    }
    
    /**
     * Setter method for property <tt>connectionId</tt>.
     *
     * @param connectionId value to be assigned to property connectionId
     */
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
    
    /**
     * Getter method for property <tt>clientIp</tt>.
     *
     * @return property value of clientIp
     */
    public String getClientIp() {
        return clientIp;
    }
    
    /**
     * Setter method for property <tt>clientIp</tt>.
     *
     * @param clientIp value to be assigned to property clientIp
     */
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    /**
     * Getter method for property <tt>clientVersion</tt>.
     *
     * @return property value of clientVersion
     */
    public String getClientVersion() {
        return clientVersion;
    }
    
    /**
     * Setter method for property <tt>clientVersion</tt>.
     *
     * @param clientVersion value to be assigned to property clientVersion
     */
    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }
}
