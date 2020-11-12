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

import java.util.List;

/**
 * ConfigChangeNotifyRequest.
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifyRequest.java, v 0.1 2020年07月14日 3:20 PM liuzunfei Exp $
 */
public class ConfigChangeNotifyRequest extends ServerPushRequest {
    
    private String dataId;
    
    private String group;
    
    private String tenant;
    
    private boolean beta;
    
    private List<String> betaIps;
    
    private String content;
    
    private String type;
    
    private boolean contentPush;
    
    public long lastModifiedTs;
    
    @Override
    public String getModule() {
        return "config";
    }
    
    /**
     * Getter method for property <tt>contentPush</tt>.
     *
     * @return property value of contentPush
     */
    public boolean isContentPush() {
        return contentPush;
    }
    
    /**
     * Setter method for property <tt>contentPush</tt>.
     *
     * @param contentPush value to be assigned to property contentPush
     */
    public void setContentPush(boolean contentPush) {
        this.contentPush = contentPush;
    }
    
    /**
     * Getter method for property <tt>lastModifiedTs</tt>.
     *
     * @return property value of lastModifiedTs
     */
    public long getLastModifiedTs() {
        return lastModifiedTs;
    }
    
    /**
     * Setter method for property <tt>lastModifiedTs</tt>.
     *
     * @param lastModifiedTs value to be assigned to property lastModifiedTs
     */
    public void setLastModifiedTs(long lastModifiedTs) {
        this.lastModifiedTs = lastModifiedTs;
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
    
    /**
     * Getter method for property <tt>beta</tt>.
     *
     * @return property value of beta
     */
    public boolean isBeta() {
        return beta;
    }
    
    /**
     * Setter method for property <tt>beta</tt>.
     *
     * @param beta value to be assigned to property beta
     */
    public void setBeta(boolean beta) {
        this.beta = beta;
    }
    
    /**
     * Getter method for property <tt>betaIps</tt>.
     *
     * @return property value of betaIps
     */
    public List<String> getBetaIps() {
        return betaIps;
    }
    
    /**
     * Setter method for property <tt>betaIps</tt>.
     *
     * @param betaIps value to be assigned to property betaIps
     */
    public void setBetaIps(List<String> betaIps) {
        this.betaIps = betaIps;
    }
    
    /**
     * Getter method for property <tt>content</tt>.
     *
     * @return property value of content
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Setter method for property <tt>content</tt>.
     *
     * @param content value to be assigned to property content
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * Getter method for property <tt>type</tt>.
     *
     * @return property value of type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Setter method for property <tt>type</tt>.
     *
     * @param type value to be assigned to property type
     */
    public void setType(String type) {
        this.type = type;
    }
}
