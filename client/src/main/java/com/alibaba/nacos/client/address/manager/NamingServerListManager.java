/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.address.manager;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.common.ModuleType;
import com.alibaba.nacos.client.env.NacosClientProperties;

/**
 * Naming Server List Manager.
 *
 * @author misakacoder
 */
public class NamingServerListManager extends AbstractServerListManager {
    
    public NamingServerListManager(NacosClientProperties properties, String namespace) throws NacosException {
        initServerList(properties, namespace);
    }
    
    @Override
    public ModuleType getModuleType() {
        return ModuleType.NAMING;
    }
}
