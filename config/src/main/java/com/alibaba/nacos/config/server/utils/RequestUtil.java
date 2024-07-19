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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;

import javax.servlet.http.HttpServletRequest;

/**
 * Request util.
 *
 * @author Nacos
 */
public class RequestUtil {
    
    public static final String CLIENT_APPNAME_HEADER = "Client-AppName";
    
    /**
     * Get real client ip from context first, if no value, use
     * {@link com.alibaba.nacos.core.utils.WebUtils#getRemoteIp(HttpServletRequest)}.
     *
     * @param request {@link HttpServletRequest}
     * @return remote ip address.
     */
    public static String getRemoteIp(HttpServletRequest request) {
        String remoteIp = RequestContextHolder.getContext().getBasicContext().getAddressContext().getSourceIp();
        if (StringUtils.isBlank(remoteIp)) {
            remoteIp = RequestContextHolder.getContext().getBasicContext().getAddressContext().getRemoteIp();
        }
        if (StringUtils.isBlank(remoteIp)) {
            remoteIp = WebUtils.getRemoteIp(request);
        }
        return remoteIp;
    }
    
    /**
     * Gets the name of the client application in the header.
     *
     * @param request {@link HttpServletRequest}
     * @return may be return null
     */
    public static String getAppName(HttpServletRequest request) {
        String result = RequestContextHolder.getContext().getBasicContext().getApp();
        return isUnknownApp(result) ? request.getHeader(CLIENT_APPNAME_HEADER) : result;
    }
    
    private static boolean isUnknownApp(String appName) {
        return StringUtils.isBlank(appName) || StringUtils.equalsIgnoreCase("unknown", appName);
    }
    
    /**
     * Gets the username of the client application in the Attribute.
     *
     * @param request {@link HttpServletRequest}
     * @return may be return null
     */
    public static String getSrcUserName(HttpServletRequest request) {
        IdentityContext identityContext = RequestContextHolder.getContext().getAuthContext().getIdentityContext();
        String result = StringUtils.EMPTY;
        if (null != identityContext) {
            result = (String) identityContext.getParameter(
                    com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID);
        }
        // If auth is disabled, get username from parameters by agreed key
        return StringUtils.isBlank(result) ? request.getParameter(Constants.USERNAME) : result;
    }
    
}
