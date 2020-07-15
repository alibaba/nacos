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
package com.alibaba.nacos.api.config.remote.request;


/**
 * @author liuzunfei
 * @version $Id: ConfigChangeListenRequest.java, v 0.1 2020年07月13日 9:01 PM liuzunfei Exp $
 */
public class ConfigChangeListenRequest extends ConfigCommonRequest {


    private static final String LISTEN="listen";
    private static final String UNLISTEN="unlisten";

    private String dataId;

    private String group;

    private String tenant;

    private String operation=LISTEN;


    public boolean isCancelListen(){
        return UNLISTEN.equals(this.operation);
    }



    public static ConfigChangeListenRequest buildListenRequest(String dataId, String group,String tenant){
        ConfigChangeListenRequest configChangeListenRequest = buildBase(dataId, group, tenant);
        configChangeListenRequest.operation=LISTEN;
        return configChangeListenRequest;
    }

    public static ConfigChangeListenRequest buildUnListenRequest(String dataId, String group,String tenant){
        ConfigChangeListenRequest configChangeListenRequest = buildBase(dataId, group, tenant);
        configChangeListenRequest.operation=UNLISTEN;
        return configChangeListenRequest;
    }


    private static ConfigChangeListenRequest  buildBase(String dataId, String group,String tenant){
        ConfigChangeListenRequest request=new ConfigChangeListenRequest();
        request.setDataId(dataId);
        request.setGroup(group);
        request.setTenant(tenant);
        return request;
    }

    @Override
    public String getType() {
        return ConfigRequestTypeConstants.CHANGE_LISTEN_CONFIG_OPERATION;
    }

    @Override
    public String getModule() {
        return "config";
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
