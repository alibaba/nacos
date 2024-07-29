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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.common.ModuleType;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;

/**
 * Config Server List Manager.
 *
 * @author misakacoder
 */
public class ConfigServerListManager extends AbstractServerListManager {
    
    private String name;
    
    private String tenant;
    
    private String namespace;
    
    private String contentPath;
    
    public ConfigServerListManager(NacosClientProperties properties) throws NacosException {
        initNamespace(properties);
        initContextPath(properties);
        initServerList(properties, namespace);
        initName(properties);
    }
    
    @Override
    public ModuleType getModuleType() {
        return ModuleType.CONFIG;
    }
    
    public String getName() {
        return name;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getContentPath() {
        return contentPath;
    }
    
    private void initNamespace(NacosClientProperties properties) {
        String namespace = TemplateUtils.stringBlankAndThenExecute(properties.getProperty(PropertyKeyConst.NAMESPACE),
                () -> "");
        this.tenant = namespace;
        this.namespace = namespace;
    }
    
    private void initContextPath(NacosClientProperties properties) {
        this.contentPath = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(PropertyKeyConst.CONTEXT_PATH), ParamUtil::getDefaultContextPath);
    }
    
    private void initName(NacosClientProperties properties) {
        this.name = TemplateUtils.stringBlankAndThenExecute(properties.getProperty(PropertyKeyConst.SERVER_NAME),
                () -> serverListProvider != null ? serverListProvider.getName() : "");
    }
}
