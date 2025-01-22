/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.config;

import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.alibaba.nacos.maintainer.client.remote.HttpRestResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration management.
 *
 * @author Nacos
 */
@Service
public class ConfigServiceImpl implements ConfigService {
    
    private static final String CONFIG_PATH_ADMIN_PATH = "/v3/admin/cs/config";
    
    @Autowired
    private ClientHttpProxy clientHttpProxy;
    
    @Override
    public String getConfig(String dataId, String groupName, String namespaceId) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        HttpRestResult<String> result = clientHttpProxy.httpGet(CONFIG_PATH_ADMIN_PATH, null, params,
                1000);
        return result.getData();
    }
}