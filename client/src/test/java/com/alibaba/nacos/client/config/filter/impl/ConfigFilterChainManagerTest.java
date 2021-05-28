/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.config.filter.impl;

import com.alibaba.nacos.api.config.filter.IConfigContext;
import com.alibaba.nacos.api.config.filter.IConfigFilter;
import com.alibaba.nacos.api.config.filter.IConfigFilterChain;
import com.alibaba.nacos.api.config.filter.IConfigRequest;
import com.alibaba.nacos.api.config.filter.IConfigResponse;
import com.alibaba.nacos.api.config.filter.IFilterConfig;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class ConfigFilterChainManagerTest {
    
    private static class MyIConfigFilter implements IConfigFilter {
        
        private String name;
        
        private int order;
        
        public MyIConfigFilter(String name, int order) {
            this.name = name;
            this.order = order;
        }
    
        @Override
        public void init(IFilterConfig filterConfig) {
        }
    
        @Override
        public void init(Properties properties) {
        }
        
        @Override
        public void doFilter(IConfigRequest request, IConfigResponse response, IConfigFilterChain filterChain)
                throws NacosException {
            IConfigContext configContext = request.getConfigContext();
            // save filter info
            configContext.setParameter(name, order);
            
            // save filter order
            if (configContext.getParameter("orders") == null) {
                configContext.setParameter("orders", new ArrayList<Integer>());
            }
            List<Integer> orders = (List<Integer>) configContext.getParameter("orders");
            orders.add(order);
            
            // save filter count
            if (configContext.getParameter("filterCount") == null) {
                configContext.setParameter("filterCount", 0);
            }
            Integer filterCount = (Integer) configContext.getParameter("filterCount");
            filterCount = filterCount + 1;
            configContext.setParameter("filterCount", filterCount);
            
            // do next
            filterChain.doFilter(request, response);
        }
        
        @Override
        public int getOrder() {
            return order;
        }
        
        @Override
        public String getFilterName() {
            return name;
        }
    }
    
    @Test
    public void testAddFilterOrder() throws NacosException {
        final ConfigFilterChainManager configFilterChainManager = new ConfigFilterChainManager(new Properties());
        MyIConfigFilter filter1 = new MyIConfigFilter("filter1", 1);
        MyIConfigFilter filter2 = new MyIConfigFilter("filter2", 2);
        MyIConfigFilter filter3 = new MyIConfigFilter("filter3", 3);
        
        //random order
        configFilterChainManager.addFilter(filter2);
        configFilterChainManager.addFilter(filter1);
        configFilterChainManager.addFilter(filter3);
        
        ConfigRequest configRequest = new ConfigRequest();
        
        configFilterChainManager.doFilter(configRequest, new ConfigResponse());
        
        IConfigContext configContext = configRequest.getConfigContext();
        
        // doFilter works
        Assert.assertEquals(1, configContext.getParameter("filter1"));
        Assert.assertEquals(2, configContext.getParameter("filter2"));
        Assert.assertEquals(3, configContext.getParameter("filter3"));
        
        //order
        List<Integer> orders = (List<Integer>) configContext.getParameter("orders");
        Assert.assertEquals(Arrays.asList(1, 2, 3), orders);
    }
    
    @Test
    public void testAddFilterNotRepeat() throws NacosException {
        final ConfigFilterChainManager configFilterChainManager = new ConfigFilterChainManager(new Properties());
        MyIConfigFilter filter1 = new MyIConfigFilter("filter1", 1);
        MyIConfigFilter filter2 = new MyIConfigFilter("filter2", 2);
        MyIConfigFilter repeatFilter = new MyIConfigFilter("filter1", 1);
        
        configFilterChainManager.addFilter(filter2);
        configFilterChainManager.addFilter(filter1);
        configFilterChainManager.addFilter(repeatFilter);
        
        ConfigRequest configRequest = new ConfigRequest();
        configFilterChainManager.doFilter(configRequest, new ConfigResponse());
        
        IConfigContext configContext = configRequest.getConfigContext();
        
        Assert.assertEquals(2, configContext.getParameter("filterCount"));
    }
}