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

import com.alibaba.nacos.api.config.filter.IConfigFilter;
import com.alibaba.nacos.api.config.filter.IConfigFilterChain;
import com.alibaba.nacos.api.config.filter.IConfigRequest;
import com.alibaba.nacos.api.config.filter.IConfigResponse;
import com.alibaba.nacos.api.config.filter.IFilterConfig;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;

public class DemoFilter1 implements IConfigFilter {
    
    private static final String DEFAULT_NAME = DemoFilter1.class.getName();
    
    @Override
    public void init(IFilterConfig filterConfig) {
    
    }
    
    @Override
    public void init(Properties properties) {
    
    }
    
    @Override
    public void doFilter(IConfigRequest request, IConfigResponse response, IConfigFilterChain filterChain)
            throws NacosException {
        request.putParameter("filter1", DEFAULT_NAME);
        filterChain.doFilter(request, response);
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
    
    @Override
    public String getFilterName() {
        return DEFAULT_NAME;
    }
}
