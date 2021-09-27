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

package com.alibaba.nacos.api.config.filter;

import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;

/**
 * Config Filter Interface.
 *
 * <p>DO NOT implement this interface directly, you should extend <code>AbstractConfigFilter</code>.
 *
 * @author Nacos
 * @see AbstractConfigFilter
 */
public interface IConfigFilter {
    
    /**
     * Init.
     *
     * @param filterConfig Filter Config
     */
    @Deprecated
    void init(IFilterConfig filterConfig);
    
    /**
     * Init.
     *
     * @param properties Filter Config
     */
    void init(Properties properties);
    
    /**
     * do filter.
     *
     * @param request     request
     * @param response    response
     * @param filterChain filter Chain
     * @throws NacosException exception
     */
    void doFilter(IConfigRequest request, IConfigResponse response, IConfigFilterChain filterChain)
            throws NacosException;
    
    /**
     * Get order.
     *
     * @return order number
     */
    int getOrder();
    
    /**
     * Get filterName.
     *
     * @return filter name
     */
    String getFilterName();
    
}
