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
 * request to query config content.
 *
 * @author liuzunfei
 * @version $Id: ConfigQueryRequest.java, v 0.1 2020年07月13日 9:06 PM liuzunfei Exp $
 */
public class ConfigQueryRequest extends AbstractConfigRequest {
    
    private static final String NOTIFY_HEADER = "notify";
    
    private String dataId;
    
    private String group;
    
    private String tenant;
    
    private String tag;
    
    /**
     * request builder.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return ConfigQueryRequest instance.
     */
    public static ConfigQueryRequest build(String dataId, String group, String tenant) {
        ConfigQueryRequest request = new ConfigQueryRequest();
        request.setDataId(dataId);
        request.setGroup(group);
        request.setTenant(tenant);
        return request;
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
    
    /**
     * Getter method for property <tt>tag</tt>.
     *
     * @return property value of tag
     */
    public String getTag() {
        return tag;
    }
    
    /**
     * Setter method for property <tt>tag</tt>.
     *
     * @param tag value to be assigned to property tag
     */
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public boolean isNotify() {
        String notify = getHeader(NOTIFY_HEADER, Boolean.FALSE.toString());
        return Boolean.parseBoolean(notify);
    }
}
