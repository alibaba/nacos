/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote.connection;

import java.util.Date;

/**
 * ConnectionMetaInfo.
 *
 * @author liuzunfei
 * @version $Id: ConnectionMetaInfo.java, v 0.1 2020年07月13日 7:28 PM liuzunfei Exp $
 */
public class ConnectionMetaInfo {
    
    /**
     * ConnectionType
     */
    String connectType;
    
    /**
     * Client IP Address
     */
    String clientIp;
    
    /**
     * Identify Unique connectionId
     */
    String connectionId;
    
    /**
     * create time
     */
    Date createTime;
    
    /**
     * astActiveTime
     */
    long lastActiveTime;
    
    public ConnectionMetaInfo(String connectionId, String clientIp, String connectType) {
        this.connectionId = connectionId;
        this.clientIp = clientIp;
        this.connectType = connectType;
        this.createTime = new Date();
        this.lastActiveTime = System.currentTimeMillis();
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
     * Getter method for property <tt>createTime</tt>.
     *
     * @return property value of createTime
     */
    public Date getCreateTime() {
        return createTime;
    }
    
    /**
     * Setter method for property <tt>createTime</tt>.
     *
     * @param createTime value to be assigned to property createTime
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
    
    /**
     * Getter method for property <tt>lastActiveTime</tt>.
     *
     * @return property value of lastActiveTime
     */
    public long getLastActiveTime() {
        return lastActiveTime;
    }
    
    /**
     * Setter method for property <tt>lastActiveTime</tt>.
     *
     * @param lastActiveTime value to be assigned to property lastActiveTime
     */
    public void setLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }
}
