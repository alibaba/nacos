/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.util;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Auth header util.
 *
 * @author xiweng.yy
 */
public class AuthHeaderUtil {
    
    /**
     * Add identity info to Http header.
     *
     * @param header     http header
     * @param authConfig nacos auth config
     */
    public static void addIdentityToHeader(Header header, NacosAuthConfig authConfig) {
        if (!authConfig.isSupportServerIdentity()) {
            return;
        }
        if (StringUtils.isNotBlank(authConfig.getServerIdentityKey())) {
            header.addParam(authConfig.getServerIdentityKey(), authConfig.getServerIdentityValue());
        }
    }
    
    /**
     * Add identity info to Grpc request header.
     *
     * @param request     grpc request
     * @param authConfig  nacos auth config
     */
    public static void addIdentityToHeader(Request request, NacosAuthConfig authConfig) {
        if (!authConfig.isSupportServerIdentity()) {
            return;
        }
        if (StringUtils.isNotBlank(authConfig.getServerIdentityKey())) {
            request.putHeader(authConfig.getServerIdentityKey(), authConfig.getServerIdentityValue());
        }
    }
    
}
