/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.roles;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.constant.RequestUrlConstants;
import com.alibaba.nacos.common.http.DefaultHttpClientFactory;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.utils.RemoteServerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Nacos builtin role service, implemented by remote request to nacos server.
 *
 * @author xiweng.yy
 */
public class NacosRoleServiceRemoteImpl extends AbstractCheckedRoleService implements NacosRoleService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosRoleServiceRemoteImpl.class);
    
    private final NacosRestTemplate nacosRestTemplate;
    
    private final AuthConfigs authConfigs;
    
    public NacosRoleServiceRemoteImpl(AuthConfigs authConfigs) {
        super(authConfigs);
        this.authConfigs = authConfigs;
        this.nacosRestTemplate = new DefaultHttpClientFactory(LOGGER).createNacosRestTemplate();
    }
    
    @Override
    public void addPermission(String role, String resource, String action) {
        Map<String, String> body = Map.of("role", role, "resource", resource, "action", action);
        try {
            HttpRestResult<String> result = nacosRestTemplate.postForm(
                    buildRemotePermissionUrlPath(AuthConstants.PERMISSION_PATH),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), null, body, String.class);
            RemoteServerUtil.singleCheckResult(result);
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    @Override
    public void deletePermission(String role, String resource, String action) {
        Query query = Query.newInstance().addParam("role", role).addParam("resource", resource)
                .addParam("action", action);
        try {
            HttpRestResult<String> result = nacosRestTemplate.delete(
                    buildRemotePermissionUrlPath(AuthConstants.PERMISSION_PATH),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), query, String.class);
            RemoteServerUtil.singleCheckResult(result);
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    @Override
    public List<PermissionInfo> getPermissions(String role) {
        if (getCachedPermissionInfoMap().containsKey(role)) {
            return getCachedPermissionInfoMap().get(role);
        }
        reload();
        return getCachedPermissionInfoMap().get(role);
    }
    
    @Override
    public Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize) {
        Query query = Query.newInstance().addParam("role", role).addParam("pageNo", pageNo)
                .addParam("pageSize", pageSize).addParam("search", "accurate");
        return getPermissionInfoPageFromRemote(query);
    }
    
    @Override
    public Page<PermissionInfo> findPermissions(String role, int pageNo, int pageSize) {
        Query query = Query.newInstance().addParam("role", role).addParam("pageNo", pageNo)
                .addParam("pageSize", pageSize).addParam("search", "blur");
        return getPermissionInfoPageFromRemote(query);
    }
    
    @Override
    public List<RoleInfo> getRoles(String username) {
        if (getCachedRoleInfoMap().containsKey(username)) {
            return getCachedRoleInfoMap().get(username);
        }
        reload();
        return getCachedRoleInfoMap().get(username);
    }
    
    @Override
    public Page<RoleInfo> getRoles(String username, String role, int pageNo, int pageSize) {
        Query query = Query.newInstance().addParam("username", username).addParam("role", role)
                .addParam("pageNo", pageNo).addParam("pageSize", pageSize).addParam("search", "accurate");
        return getRoleInfoPageFromRemote(query);
    }
    
    @Override
    public Page<RoleInfo> findRoles(String username, String role, int pageNo, int pageSize) {
        Query query = Query.newInstance().addParam("username", username).addParam("role", role)
                .addParam("pageNo", pageNo).addParam("pageSize", pageSize).addParam("search", "blur");
        return getRoleInfoPageFromRemote(query);
    }
    
    @Override
    public List<String> findRoleNames(String role) {
        Query query = Query.newInstance().addParam("role", role);
        try {
            HttpRestResult<String> httpResult = nacosRestTemplate.get(
                    buildRemoteRoleUrlPath(AuthConstants.ROLE_PATH + "/search"),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), query, String.class);
            RemoteServerUtil.singleCheckResult(httpResult);
            Result<List<String>> result = JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
            return result.getData();
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    @Override
    public List<RoleInfo> getAllRoles() {
        return getRoles(StringUtils.EMPTY, StringUtils.EMPTY, DEFAULT_PAGE_NO, Integer.MAX_VALUE).getPageItems();
    }
    
    @Override
    public void addRole(String role, String username) {
        if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(role)) {
            throw new IllegalArgumentException(
                    "role '" + AuthConstants.GLOBAL_ADMIN_ROLE + "' is not permitted to create!");
        }
        Map<String, String> body = Map.of("role", role, "username", username);
        try {
            HttpRestResult<String> httpResult = nacosRestTemplate.postForm(
                    buildRemoteRoleUrlPath(AuthConstants.ROLE_PATH),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), body, String.class);
            RemoteServerUtil.singleCheckResult(httpResult);
            getCachedRoleSet().add(role);
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    @Override
    public void deleteRole(String role, String userName) {
        if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(role)) {
            throw new IllegalArgumentException(
                    "role '" + AuthConstants.GLOBAL_ADMIN_ROLE + "' is not permitted to delete!");
        }
        Query query = Query.newInstance().addParam("role", role).addParam("userName", userName);
        try {
            HttpRestResult<String> result = nacosRestTemplate.delete(buildRemoteRoleUrlPath(AuthConstants.ROLE_PATH),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), query, String.class);
            RemoteServerUtil.singleCheckResult(result);
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    @Override
    public void deleteRole(String role) {
        if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(role)) {
            throw new IllegalArgumentException(
                    "role '" + AuthConstants.GLOBAL_ADMIN_ROLE + "' is not permitted to delete!");
        }
        Query query = Query.newInstance().addParam("role", role);
        try {
            HttpRestResult<String> result = nacosRestTemplate.delete(buildRemoteRoleUrlPath(AuthConstants.ROLE_PATH),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), query, String.class);
            RemoteServerUtil.singleCheckResult(result);
            getCachedRoleSet().remove(role);
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    @Override
    public void addAdminRole(String username) {
        // if has global admin role, means already synced admin role to console cached.
        if (hasGlobalAdminRole()) {
            return;
        }
        // No need to call add admin role. In {@link NacosUserServiceRemoteImpl#createUser},
        // it will call create admin role which include add admin role operation.
        getCachedRoleSet().add(AuthConstants.GLOBAL_ADMIN_ROLE);
        authConfigs.setHasGlobalAdminRole(true);
    }
    
    private String buildRemotePermissionUrlPath(String apiPath) {
        return RequestUrlConstants.HTTP_PREFIX + RemoteServerUtil.getOneNacosServerAddress()
                + RemoteServerUtil.getRemoteServerContextPath() + apiPath;
    }
    
    private Page<PermissionInfo> getPermissionInfoPageFromRemote(Query query) {
        try {
            HttpRestResult<String> httpResult = nacosRestTemplate.get(
                    buildRemotePermissionUrlPath(AuthConstants.PERMISSION_PATH + "/list"),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), query, String.class);
            RemoteServerUtil.singleCheckResult(httpResult);
            Result<Page<PermissionInfo>> result = JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
            return result.getData();
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    private String buildRemoteRoleUrlPath(String apiPath) {
        return RequestUrlConstants.HTTP_PREFIX + RemoteServerUtil.getOneNacosServerAddress()
                + RemoteServerUtil.getRemoteServerContextPath() + apiPath;
    }
    
    private Page<RoleInfo> getRoleInfoPageFromRemote(Query query) {
        try {
            HttpRestResult<String> httpResult = nacosRestTemplate.get(
                    buildRemoteRoleUrlPath(AuthConstants.ROLE_PATH + "/list"),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), query, String.class);
            RemoteServerUtil.singleCheckResult(httpResult);
            Result<Page<RoleInfo>> result = JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
            return result.getData();
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
}
