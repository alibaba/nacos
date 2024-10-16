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

package com.alibaba.nacos.client.address.mock;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.ServerListProvider;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;

import java.util.Collections;
import java.util.List;

public class MockServerListProvider implements ServerListProvider {
    
    private NacosClientProperties properties;
    
    @Override
    public void init(NacosClientProperties properties, NacosRestTemplate nacosRestTemplate) throws NacosException {
        this.properties = properties;
        nacosRestTemplate.getInterceptors();
    }
    
    @Override
    public List<String> getServerList() {
        if (properties.containsKey("EmptyList")) {
            return Collections.emptyList();
        }
        return Collections.singletonList("mock-server-list");
    }
    
    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
    
    @Override
    public boolean match(NacosClientProperties properties) {
        return properties.containsKey("MockTest");
    }
    
    @Override
    public void shutdown() throws NacosException {
    }
    
    @Override
    public String getServerName() {
        if (isReturnMock()) {
            return "MockServerName";
        }
        return ServerListProvider.super.getServerName();
    }
    
    @Override
    public String getNamespace() {
        if (isReturnMock()) {
            return "MockNamespace";
        }
        return ServerListProvider.super.getNamespace();
    }
    
    @Override
    public String getContextPath() {
        if (isReturnMock()) {
            return "MockContextPath";
        }
        return ServerListProvider.super.getContextPath();
    }
    
    @Override
    public boolean isFixed() {
        if (isReturnMock()) {
            return true;
        }
        return ServerListProvider.super.isFixed();
    }
    
    @Override
    public String getAddressSource() {
        if (isReturnMock()) {
            return "MockAddressSource";
        }
        return ServerListProvider.super.getAddressSource();
    }
    
    private boolean isReturnMock() {
        return properties.getBoolean("ReturnMock", false);
    }
}
