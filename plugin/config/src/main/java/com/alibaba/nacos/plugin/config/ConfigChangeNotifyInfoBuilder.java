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

package com.alibaba.nacos.plugin.config;

import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.plugin.config.model.ConfigChangeNotifyInfo;

/**
 * ConfigChangeNotifyInfoBuilder.
 *
 * @author liyunfei
 */
public class ConfigChangeNotifyInfoBuilder {
    
    private ConfigChangeNotifyInfo configChangeNotifyInfo;
    
    private ConfigChangeNotifyInfoBuilder() {
    
    }
    
    public static ConfigChangeNotifyInfoBuilder newBuilder() {
        return new ConfigChangeNotifyInfoBuilder();
    }
    
    public ConfigChangeNotifyInfo build() {
        return configChangeNotifyInfo;
    }
    
    /**
     * Config change base info ,which is mandatory.
     */
    public ConfigChangeNotifyInfoBuilder basicInfo(String action, Boolean rs, String modifyTime) {
        configChangeNotifyInfo = new ConfigChangeNotifyInfo(action, rs, modifyTime);
        return this;
    }
    
    /**
     * Operate user relevant info.
     */
    public ConfigChangeNotifyInfoBuilder sourceInfo(String srcUser, String srcIp, String use, String appName) {
        if (configChangeNotifyInfo == null) {
            throw new NullPointerException("[ConfigChangeNotifyInfoBuilder] didnt create basicInfo");
        }
        configChangeNotifyInfo.setSrcUser(srcUser);
        configChangeNotifyInfo.setSrcIp(srcIp);
        configChangeNotifyInfo.setUse(use);
        configChangeNotifyInfo.setAppName(appName);
        return this;
    }
    
    /**
     * Build information of publish or update.
     */
    public ConfigChangeNotifyInfoBuilder publishOrUpdateInfo(String dataId, String group, String tenant, String content,
            String type, String tag, String configTags, String effect, String desc) {
        if (configChangeNotifyInfo == null) {
            throw new NullPointerException("[ConfigChangeNotifyInfoBuilder] didnt create basicInfo");
        }
        configChangeNotifyInfo.setDataId(dataId);
        configChangeNotifyInfo.setGroup(group);
        configChangeNotifyInfo.setTenant(tenant);
        configChangeNotifyInfo.setContent(content);
        configChangeNotifyInfo.setType(type);
        configChangeNotifyInfo.setEffect(effect);
        configChangeNotifyInfo.setTag(tag);
        configChangeNotifyInfo.setConfigTags(configTags);
        configChangeNotifyInfo.setDesc(desc);
        return this;
    }
    
    /**
     * Build information of remove.
     */
    public ConfigChangeNotifyInfoBuilder removeInfo(String dataId) {
        if (configChangeNotifyInfo == null) {
            throw new NullPointerException("[ConfigChangeNotifyInfoBuilder] didnt create basicInfo");
        }
        configChangeNotifyInfo.setDataId(dataId);
        return this;
    }
    
    /**
     * Build information of import.
     */
    public ConfigChangeNotifyInfoBuilder importInfo(String namespace, SameConfigPolicy policy) {
        if (configChangeNotifyInfo == null) {
            throw new NullPointerException("[ConfigChangeNotifyInfoBuilder] didnt create basicInfo");
        }
        configChangeNotifyInfo.setNamespace(namespace);
        configChangeNotifyInfo.setPolicy(policy);
        return this;
    }
}
