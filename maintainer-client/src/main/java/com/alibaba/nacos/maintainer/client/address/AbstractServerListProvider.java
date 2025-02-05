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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.env.NacosClientProperties;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;

import java.util.List;

/**
 * Address server list provider.
 * 
 * @author Nacos
 */
public abstract class AbstractServerListProvider implements ServerListProvider {
    
    protected String contextPath = ParamUtil.getDefaultContextPath();
    
    @Override
    public void init(final NacosClientProperties properties, final NacosRestTemplate nacosRestTemplate) throws NacosException {
        if (null == properties) {
            throw new NacosException(NacosException.INVALID_PARAM, "properties is null");
        }
        initContextPath(properties);
    }
    
    /**
     * Get server list.
     * @return server list
     */
    @Override
    public abstract List<String> getServerList();
    
    /**
     * Get order.
     * @return order
     */
    @Override
    public abstract int getOrder();
    
    public String getContextPath() {
        return contextPath;
    }
    
    private void initContextPath(NacosClientProperties properties) {
        String contentPathTmp = properties.getProperty(PropertyKeyConst.CONTEXT_PATH);
        if (!StringUtils.isBlank(contentPathTmp)) {
            this.contextPath = contentPathTmp;
        }
    }
}
