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

package com.alibaba.nacos.naming.utils;

import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AddressContext;
import com.alibaba.nacos.core.utils.WebUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Naming request util.
 *
 * @author xiweng.yy
 */
public class NamingRequestUtil {
    
    /**
     * Get source ip from request context.
     *
     * @return source ip, null if not found
     */
    public static String getSourceIp() {
        AddressContext addressContext = RequestContextHolder.getContext().getBasicContext().getAddressContext();
        String sourceIp = addressContext.getSourceIp();
        if (StringUtils.isBlank(sourceIp)) {
            sourceIp = addressContext.getRemoteIp();
        }
        return sourceIp;
    }
    
    /**
     * Get source ip from request context first, if it can't found, get from http request.
     *
     * @param httpServletRequest http request
     * @return source ip, null if not found
     */
    public static String getSourceIpForHttpRequest(HttpServletRequest httpServletRequest) {
        String sourceIp = getSourceIp();
        // If can't get from request context, get from http request.
        if (StringUtils.isBlank(sourceIp)) {
            sourceIp = WebUtils.getRemoteIp(httpServletRequest);
        }
        return sourceIp;
    }
    
    /**
     * Get source ip from request context first, if it can't found, get from http request.
     *
     * @param meta grpc request meta
     * @return source ip, null if not found
     */
    public static String getSourceIpForGrpcRequest(RequestMeta meta) {
        String sourceIp = getSourceIp();
        // If can't get from request context, get from grpc request meta.
        if (StringUtils.isBlank(sourceIp)) {
            sourceIp = meta.getClientIp();
        }
        return sourceIp;
    }
}
