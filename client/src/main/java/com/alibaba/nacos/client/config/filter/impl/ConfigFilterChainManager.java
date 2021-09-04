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
import com.alibaba.nacos.api.exception.NacosException;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * Config Filter Chain Management.
 *
 * @author Nacos
 */
public class ConfigFilterChainManager implements IConfigFilterChain {
    
    private final List<IConfigFilter> filters = new ArrayList<IConfigFilter>();
    
    public ConfigFilterChainManager(Properties properties) {
        // 读取所有IConfigFilter的插件化实现。
        // 在单元测试中找到相应的插件化实现的例子（比如DemoFilter1）。
        ServiceLoader<IConfigFilter> configFilters = ServiceLoader.load(IConfigFilter.class);
        for (IConfigFilter configFilter : configFilters) {
            configFilter.init(properties);
            addFilter(configFilter);
        }
    }
    
    /**
     * Add filter.
     *
     * @param filter filter
     * @return this
     */
    public synchronized ConfigFilterChainManager addFilter(IConfigFilter filter) {
        // ordered by order value
        // 在增加filter的时候，要注意filter的顺序，即添加的这个filter在所有filter中排第几。
        int i = 0;
        while (i < this.filters.size()) {
            IConfigFilter currentValue = this.filters.get(i);
            if (currentValue.getFilterName().equals(filter.getFilterName())) {
                break;
            }
            if (filter.getOrder() >= currentValue.getOrder() && i < this.filters.size()) {
                i++;
            } else {
                this.filters.add(i, filter);
                break;
            }
        }
        
        if (i == this.filters.size()) {
            this.filters.add(i, filter);
        }
        return this;
    }
    
    /**
     * 对于配置的增删改查request以及response进行拦截。
     * 用户可以实现通过实现自己的插件，来对于request以及response进行一些操作。
     * 比如可以在request中增加一个字段，参考单元测试中的 {@DemoFilter1.java}
     */
    @Override
    public void doFilter(IConfigRequest request, IConfigResponse response) throws NacosException {
        // 为什么要封装一个内部类VirtualFilterChain？其实完全可以直接通过一个for循环遍历所有的filters，然后一一filter呀。
        new VirtualFilterChain(this.filters).doFilter(request, response);
    }
    
    // 封装一个内部类VirtualFilterChain，用来进行doFilter筛选过程的具体逻辑，个人感觉有些冗余。
    private static class VirtualFilterChain implements IConfigFilterChain {
        
        private final List<? extends IConfigFilter> additionalFilters;
        
        private int currentPosition = 0;
        
        public VirtualFilterChain(List<? extends IConfigFilter> additionalFilters) {
            this.additionalFilters = additionalFilters;
        }
        
        @Override
        public void doFilter(final IConfigRequest request, final IConfigResponse response) throws NacosException {
            if (this.currentPosition != this.additionalFilters.size()) {
                this.currentPosition++;
                IConfigFilter nextFilter = this.additionalFilters.get(this.currentPosition - 1);
                nextFilter.doFilter(request, response, this);
            }
        }
    }
    
}
