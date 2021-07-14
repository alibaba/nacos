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
 * ConnectResetRequest.
 *
 * @author liuzunfei
 * @version $Id: ConnectResetRequest.java, v 0.1 2020年07月15日 11:11 AM liuzunfei Exp $
 */
public class ConnectResetRequest extends ServerRequest {
    
    private static final String MODULE = "internal";
    
    String serverIp;
    
    String serverPort;
    
    @Override
    public String getModule() {
        return MODULE;
    }
    
    /**
     * Getter method for property <tt>serverIp</tt>.
     *
     * @return property value of serverIp
     */
    public String getServerIp() {
        return serverIp;
    }
    
    /**
     * Setter method for property <tt>serverIp</tt>.
     *
     * @param serverIp value to be assigned to property serverIp
     */
    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }
    
    /**
     * Getter method for property <tt>serverPort</tt>.
     *
     * @return property value of serverPort
     */
    public String getServerPort() {
        return serverPort;
    }
    
    /**
     * Setter method for property <tt>serverPort</tt>.
     *
     * @param serverPort value to be assigned to property serverPort
     */
    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }
}
