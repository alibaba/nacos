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
package com.alibaba.nacos.api.config.remote.response;

import com.alibaba.nacos.api.remote.response.Response;

/**
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifyResponse.java, v 0.1 2020年07月14日 3:20 PM liuzunfei Exp $
 */
public class ConfigChangeNotifyResponse extends Response {
    
    
    private String ackId;

    private String dataId;

    private String group;

    private String tenant;
    
    
    public ConfigChangeNotifyResponse(int resultCode, String message) {
        super(ConfigResponseTypeConstants.CONFIG_CHANGE_NOTIFY, resultCode, message);
    }
    
    /**
     * Getter method for property <tt>ackId</tt>.
     *
     * @return property value of ackId
     */
    public String getAckId() {
        return ackId;
    }
    
    /**
     * Setter method for property <tt>ackId</tt>.
     *
     * @param ackId value to be assigned to property ackId
     */
    public void setAckId(String ackId) {
        this.ackId = ackId;
    }

    /**
     * Getter method for property <tt>dataId</tt>.
     *
     * @return property value of dataId
     */
    public String getDataId() {
        return dataId;
    }

    /**
     * Setter method for property <tt>dataId</tt>.
     *
     * @param dataId value to be assigned to property dataId
     */
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    /**
     * Getter method for property <tt>group</tt>.
     *
     * @return property value of group
     */
    public String getGroup() {
        return group;
    }

    /**
     * Setter method for property <tt>group</tt>.
     *
     * @param group value to be assigned to property group
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * Getter method for property <tt>tenant</tt>.
     *
     * @return property value of tenant
     */
    public String getTenant() {
        return tenant;
    }

    /**
     * Setter method for property <tt>tenant</tt>.
     *
     * @param tenant value to be assigned to property tenant
     */
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
