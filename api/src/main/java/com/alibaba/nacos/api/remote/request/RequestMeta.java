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

import java.util.HashMap;
import java.util.Map;

/**
 * RequestMeta info.
 *
 * @author liuzunfei
 * @version $Id: RequestMeta.java, v 0.1 2020年07月14日 10:32 AM liuzunfei Exp $
 */
public class RequestMeta {
    
    private String connectionId = "";
    
    private String clientIp = "";
    
    private String clientVersion = "";
    
    private Map<String, String> labels = new HashMap<String, String>();

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
    
    /**
     * Getter method for property <tt>labels</tt>.
     *
     * @return property value of labels
     */
    public Map<String, String> getLabels() {
        return labels;
    }
    
    /**
     * Setter method for property <tt>labels</tt>.
     *
     * @param labels value to be assigned to property labels
     */
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
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
    
    @Override
    public String toString() {
        return "RequestMeta{" + "connectionId='" + connectionId + '\'' + ", clientIp='" + clientIp + '\''
                + ", clientVersion='" + clientVersion + '\'' + ", labels=" + labels + '}';
    }
}
