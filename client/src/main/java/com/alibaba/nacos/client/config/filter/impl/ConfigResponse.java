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

package com.alibaba.nacos.client.config.filter.impl;

import com.alibaba.nacos.api.config.filter.IConfigContext;
import com.alibaba.nacos.api.config.filter.IConfigResponse;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.nacos.client.config.common.ConfigConstants.CONFIG_TYPE;
import static com.alibaba.nacos.client.config.common.ConfigConstants.CONTENT;
import static com.alibaba.nacos.client.config.common.ConfigConstants.DATA_ID;
import static com.alibaba.nacos.client.config.common.ConfigConstants.ENCRYPTED_DATA_KEY;
import static com.alibaba.nacos.client.config.common.ConfigConstants.GROUP;
import static com.alibaba.nacos.client.config.common.ConfigConstants.TENANT;

/**
 * Config Response.
 *
 * @author Nacos
 */
public class ConfigResponse implements IConfigResponse {
    
    private final Map<String, Object> param = new HashMap<String, Object>();
    
    private final IConfigContext configContext = new ConfigContext();
    
    public String getTenant() {
        return (String) param.get(TENANT);
    }
    
    public void setTenant(String tenant) {
        param.put(TENANT, tenant);
    }
    
    public String getDataId() {
        return (String) param.get(DATA_ID);
    }
    
    public void setDataId(String dataId) {
        param.put(DATA_ID, dataId);
    }
    
    public String getGroup() {
        return (String) param.get(GROUP);
    }
    
    public void setGroup(String group) {
        param.put(GROUP, group);
    }
    
    public String getContent() {
        return (String) param.get(CONTENT);
    }
    
    public void setContent(String content) {
        param.put(CONTENT, content);
    }
    
    public String getConfigType() {
        return (String) param.get(CONFIG_TYPE);
    }
    
    public void setConfigType(String configType) {
        param.put(CONFIG_TYPE, configType);
    }
    
    public String getEncryptedDataKey() {
        return (String) param.get(ENCRYPTED_DATA_KEY);
    }
    
    public void setEncryptedDataKey(String encryptedDataKey) {
        param.put(ENCRYPTED_DATA_KEY, encryptedDataKey);
    }
    
    @Override
    public Object getParameter(String key) {
        return param.get(key);
    }
    
    @Override
    public void putParameter(String key, Object value) {
        param.put(key, value);
    }
    
    @Override
    public IConfigContext getConfigContext() {
        return configContext;
    }
    
}
