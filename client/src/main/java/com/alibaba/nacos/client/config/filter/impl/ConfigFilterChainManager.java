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
    
    @Override
    public void doFilter(IConfigRequest request, IConfigResponse response) throws NacosException {
        new VirtualFilterChain(this.filters).doFilter(request, response);
    }
    
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
