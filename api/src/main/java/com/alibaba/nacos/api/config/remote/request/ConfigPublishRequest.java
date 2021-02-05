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

import java.util.HashMap;
import java.util.Map;

/**
 * request to publish a config.
 *
 * @author liuzunfei
 * @version $Id: ConfigPublishRequest.java, v 0.1 2020年07月16日 4:30 PM liuzunfei Exp $
 */
public class ConfigPublishRequest extends AbstractConfigRequest {
    
    String dataId;
    
    String group;
    
    String tenant;
    
    String content;
    
    String casMd5;
    
    private Map<String, String> additionMap;
    
    public ConfigPublishRequest() {
    
    }
    
    /**
     * get additional param.
     *
     * @param key key of param.
     * @return value of param ,return null if not exist.
     */
    public String getAdditionParam(String key) {
        return additionMap == null ? null : additionMap.get(key);
    }
    
    /**
     * put additional param value. will override if exist.
     *
     * @param key   key of param.
     * @param value value of param.
     */
    public void putAdditionalParam(String key, String value) {
        if (additionMap == null) {
            additionMap = new HashMap<String, String>(2);
        }
        additionMap.put(key, value);
    }
    
    public ConfigPublishRequest(String dataId, String group, String tenant, String content) {
        this.content = content;
        this.dataId = dataId;
        this.group = group;
        this.tenant = tenant;
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
     * Getter method for property <tt>casMd5</tt>.
     *
     * @return property value of casMd5
     */
    public String getCasMd5() {
        return casMd5;
    }
    
    /**
     * Setter method for property <tt>casMd5</tt>.
     *
     * @param casMd5 value to be assigned to property content
     */
    public void setCasMd5(String casMd5) {
        this.casMd5 = casMd5;
    }
    
    /**
     * Getter method for property <tt>casMd5</tt>.
     *
     * @return property value of casMd5
     */
    public Map<String, String> getAdditionMap() {
        return additionMap;
    }
    
    /**
     * Setter method for property <tt>additionMap</tt>.
     *
     * @param additionMap value to be assigned to property additionMap
     */
    public void setAdditionMap(Map<String, String> additionMap) {
        this.additionMap = additionMap;
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
