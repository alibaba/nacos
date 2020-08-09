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

package com.alibaba.nacos.api.config.remote.request;

import com.alibaba.nacos.api.remote.request.ServerPushRequest;

/**
 * ConfigChangeNotifyRequest.
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifyRequest.java, v 0.1 2020年07月14日 3:20 PM liuzunfei Exp $
 */
public class ConfigChangeNotifyRequest extends ServerPushRequest {
    
    private String dataId;
    
    private String group;
    
    private String tenant;
    
    @Override
    public String getType() {
        return ConfigRequestTypeConstants.CONFIG_CHANGE_NOTIFY;
    }
    
    @Override
    public String getModule() {
        return "config";
    }
    
    /**
     * build success response.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return ConfigChangeNotifyResponse
     */
    public static ConfigChangeNotifyRequest build(String dataId, String group, String tenant) {
        ConfigChangeNotifyRequest response = new ConfigChangeNotifyRequest();
        response.setDataId(dataId);
        response.setGroup(group);
        response.setTenant(tenant);
        return response;
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
    
    @Override
    public String toString() {
        return "ConfigChangeNotifyResponse{" + "dataId='" + dataId + '\'' + ", group='" + group + '\'' + ", tenant='"
                + tenant + '\'' + '}';
    }
}
