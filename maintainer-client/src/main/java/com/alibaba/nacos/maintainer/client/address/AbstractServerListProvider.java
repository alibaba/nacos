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

package com.alibaba.nacos.maintainer.client.address;

import com.alibaba.nacos.maintainer.client.constants.PropertyKeyConstants;
import com.alibaba.nacos.maintainer.client.env.NacosClientProperties;
import com.alibaba.nacos.maintainer.client.exception.NacosException;
import com.alibaba.nacos.maintainer.client.remote.client.NacosRestTemplate;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.alibaba.nacos.maintainer.client.utils.StringUtils;

import java.util.List;

/**
 * Address server list provider.
 * 
 * @author Nacos
 */
public abstract class AbstractServerListProvider implements ServerListProvider {
    
    protected String contextPath = ParamUtil.getDefaultContextPath();
    
    protected String namespace = "";
    
    @Override
    public void init(final NacosClientProperties properties, final NacosRestTemplate nacosRestTemplate) throws NacosException {
        if (null == properties) {
            throw new NacosException(NacosException.INVALID_PARAM, "properties is null");
        }
        initContextPath(properties);
        initNameSpace(properties);
    }
    
    /**
     * Get server list.
     * @return server list
     */
    @Override
    public abstract List<String> getServerList();
    
    /**
     * Get server name.
     * @return server name
     */
    @Override
    public abstract String getServerName();
    
    /**
     * Get order.
     * @return order
     */
    @Override
    public abstract int getOrder();
    
    public String getContextPath() {
        return contextPath;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    private void initContextPath(NacosClientProperties properties) {
        String contentPathTmp = properties.getProperty(PropertyKeyConstants.CONTEXT_PATH);
        if (!StringUtils.isBlank(contentPathTmp)) {
            this.contextPath = contentPathTmp;
        }
    }
    
    private void initNameSpace(NacosClientProperties properties) {
        String namespace = properties.getProperty(PropertyKeyConstants.NAMESPACE);
        if (StringUtils.isNotBlank(namespace)) {
            this.namespace = namespace;
        }
    }
}
