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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Map;

/**
 * Connection.
 *
 * @author liuzunfei
 * @version $Id: Connection.java, v 0.1 2020年07月13日 7:08 PM liuzunfei Exp $
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class Connection {
    
    private final ConnectionMetaInfo metaInfo;
    
    public Connection(ConnectionMetaInfo metaInfo) {
        this.metaInfo = metaInfo;
    }
    
    /**
     * Send response to this client that associated to this connection.
     *
     * @param request request
     */
    public abstract Response sendRequest(Request request, long timeoutMills) throws NacosException;
    
    /**
     * Send response to this client that associated to this connection.
     *
     * @param request request
     */
    public abstract void sendRequestNoAck(Request request) throws NacosException;
    
    /**
     * Send response to this client that associated to this connection.
     *
     * @param request request.
     */
    public abstract RequestFuture sendRequestWithFuture(Request request) throws NacosException;
    
    /**
     * Send response to this client that associated to this connection.
     *
     * @param request request.
     */
    public abstract void sendRequestWithCallBack(Request request, RequestCallBack callBack)
            throws NacosException;
    
    /**
     * Close this connection, if this connection is not active yet.
     */
    public abstract void closeGrapcefully();
    
    /**
     * Update last Active Time to now.
     */
    public void freshActiveTime() {
        metaInfo.setLastActiveTime(System.currentTimeMillis());
    }
    
    /**
     * return last active time, include request occurs and.
     *
     * @return
     */
    public long getLastActiveTimestamp() {
        return metaInfo.lastActiveTime;
    }
    
    public String getConnectionId() {
        return metaInfo.connectionId;
    }
    
    /**
     * check if this connection is sdk source.
     *
     * @return if this connection is sdk source.
     */
    public boolean isSdkSource() {
        Map<String, String> labels = metaInfo.labels;
        String source = labels.get(RemoteConstants.LABEL_SOURCE);
        return RemoteConstants.LABEL_SOURCE_SDK.equalsIgnoreCase(source);
    }
    
    /**
     * Getter method for property <tt>metaInfo</tt>.
     *
     * @return property value of metaInfo
     */
    public ConnectionMetaInfo getMetaInfo() {
        return metaInfo;
    }
    
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}

