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

/**
 * ConfigChangeNotifyRequest.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifyRequest.java, v 0.1 2020年07月14日 3:20 PM liuzunfei Exp $
 */
public class ConfigChangeNotifyRequest extends ConfigReSyncRequest {
    
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
}
