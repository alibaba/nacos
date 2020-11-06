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

package com.alibaba.nacos.console.filter;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.console.service.NamespaceServiceImpl;
import com.alibaba.nacos.core.utils.WebUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * param check filter.
 *
 * @author horizonzy
 * @since 1.4.0
 */
public class ParamCheckFilter extends OncePerRequestFilter {
    
    private final NamespaceServiceImpl namespaceService;
    
    public ParamCheckFilter(NamespaceServiceImpl namespaceService) {
        this.namespaceService = namespaceService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        checkTenantIsExist(request);
        chain.doFilter(request, response);
    }
    
    private void checkTenantIsExist(HttpServletRequest request) {
        final String namespaceId = WebUtils
                .optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        if (!namespaceService.tenantIsExist(namespaceId)) {
            throw new IllegalArgumentException(
                    "Param 'namespaceId':{" + namespaceId + "} is not exist, please create it firstly'");
        }
    }
}
