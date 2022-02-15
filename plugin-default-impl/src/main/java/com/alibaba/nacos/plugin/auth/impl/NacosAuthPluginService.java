/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.impl.users.User;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Nacos default auth plugin service implementation.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosAuthPluginService implements AuthPluginService {
    
    private static final String USER_IDENTITY_PARAM_KEY = "user";
    
    private static final List<String> IDENTITY_NAMES = new LinkedList<String>() {
        {
            add(AuthConstants.AUTHORIZATION_HEADER);
            add(Constants.ACCESS_TOKEN);
            add(AuthConstants.PARAM_USERNAME);
            add(AuthConstants.PARAM_PASSWORD);
        }
    };
    
    private NacosAuthManager nacosAuthManager;
    
    @Override
    public Collection<String> identityNames() {
        return IDENTITY_NAMES;
    }
    
    @Override
    public boolean enableAuth(ActionTypes action, String type) {
        // enable all of action and type
        return true;
    }
    
    @Override
    public boolean validateIdentity(IdentityContext identityContext, Resource resource) throws AccessException {
        checkNacosAuthManager();
        User user = nacosAuthManager.login(identityContext);
        identityContext.setParameter(USER_IDENTITY_PARAM_KEY, user);
        identityContext.setParameter(com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID,
                user.getUserName());
        return true;
    }
    
    @Override
    public Boolean validateAuthority(IdentityContext identityContext, Permission permission) throws AccessException {
        NacosUser user = (NacosUser) identityContext.getParameter(USER_IDENTITY_PARAM_KEY);
        nacosAuthManager.auth(permission, user);
        return true;
    }
    
    @Override
    public String getAuthServiceName() {
        return AuthConstants.AUTH_PLUGIN_TYPE;
    }
    
    private void checkNacosAuthManager() {
        if (null == nacosAuthManager) {
            nacosAuthManager = ApplicationUtils.getBean(NacosAuthManager.class);
        }
    }
}
