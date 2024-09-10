/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 * request to remove a config .
 *
 * @author liuzunfei
 * @version $Id : ConfigRemoveRequest.java, v 0.1 2020年07月16日 4:31 PM liuzunfei Exp $
 */
public class ConfigRemoveRequest extends AbstractConfigRequest {
    
    /**
     * The Tag.
     */
    String tag;
    
    private Map<String, String> additionMap;
    
    /**
     * Instantiates a new Config remove request.
     */
    public ConfigRemoveRequest() {
    
    }
    
    /**
     * Instantiates a new Config remove request.
     *
     * @param dataId the data id
     * @param group  the group
     * @param tenant the tenant
     * @param tag    the tag
     */
    public ConfigRemoveRequest(String dataId, String group, String tenant, String tag) {
        super.setDataId(dataId);
        super.setGroup(group);
        super.setTenant(tenant);
        this.tag = tag;
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
            additionMap = new HashMap<>(2);
        }
        additionMap.put(key, value);
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
    
    /**
     * Sets addition map.
     *
     * @param additionMap the addition map
     */
    public void setAdditionMap(Map<String, String> additionMap) {
        this.additionMap = additionMap;
    }
    
    /**
     * Gets addition map.
     *
     * @return the addition map
     */
    public Map<String, String> getAdditionMap() {
        return additionMap;
    }
    
}
