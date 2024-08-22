/*
 *
 *  * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.spi.server.AppAuthPluginService;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * Nacos default app auth plugin service implementation.
 */
public class NacosAppAuthPluginService implements AppAuthPluginService {
    
    @Override
    public Map<String, Set<String>> getAppPermissions(IdentityContext identityContext) {
        Map<String, Set<String>> appPermissionMap = Maps.newHashMap();
        String userName = getUserName(identityContext);
        if (null != userName) {
            RolePersistService rolePersistService = ApplicationUtils.getBean(RolePersistService.class);
            appPermissionMap = rolePersistService.getAppPermissions(userName);
        }
        Set<String> appNames = Sets.newHashSet(appPermissionMap.keySet());
        identityContext.setParameter(Constants.Identity.APP_PERMISSIONS, appPermissionMap);
        identityContext.setParameter(AuthConstants.APP_NAME, appNames);
        return appPermissionMap;
    }
    
    @Override
    public String getAuthServiceName() {
        return AuthConstants.AUTH_PLUGIN_TYPE;
    }
    
    /**
     * Get login username.
     * @param identityContext identityContext.
     * @return
     */
    private String getUserName(IdentityContext identityContext) {
        Object userName = identityContext.getParameter(Constants.Identity.IDENTITY_ID);
        if (null != userName) {
            return (String) userName;
        }
        userName = identityContext.getParameter(AuthConstants.PARAM_USERNAME);
        if (null != userName) {
            return (String) userName;
        }
        return null;
    }
}
