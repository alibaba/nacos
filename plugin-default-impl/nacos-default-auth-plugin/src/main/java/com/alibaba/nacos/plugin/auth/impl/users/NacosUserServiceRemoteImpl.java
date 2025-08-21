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

package com.alibaba.nacos.plugin.auth.impl.users;

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
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.utils.RemoteServerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Map;

/**
 * Custom user service, implemented by remote request to nacos server.
 *
 * @author xiweng.yy
 */
public class NacosUserServiceRemoteImpl extends AbstractCachedUserService implements NacosUserService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosUserServiceRemoteImpl.class);
    
    private final NacosRestTemplate nacosRestTemplate;
    
    private final AuthConfigs authConfigs;
    
    public NacosUserServiceRemoteImpl(AuthConfigs authConfigs) {
        super();
        this.authConfigs = authConfigs;
        this.nacosRestTemplate = new DefaultHttpClientFactory(LOGGER).createNacosRestTemplate();
    }
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = getUser(username);
        if (null == user) {
            throw new UsernameNotFoundException(String.format("User %s not found", username));
        }
        return new NacosUserDetails(user);
    }
    
    @Override
    public void updateUserPassword(String username, String password) {
        Query query = Query.newInstance().addParam("username", username);
        Map<String, String> body = Map.of("newPassword", password);
        try {
            HttpRestResult<String> result = nacosRestTemplate.putForm(buildRemoteUserUrlPath(AuthConstants.USER_PATH),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), query, body, String.class);
            RemoteServerUtil.singleCheckResult(result);
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    @Override
    public Page<User> getUsers(int pageNo, int pageSize, String username) {
        Query query = Query.newInstance().addParam("username", username).addParam("pageNo", pageNo)
                .addParam("pageSize", pageSize).addParam("search", "accurate");
        return getUserPageFromRemote(query);
    }
    
    @Override
    public Page<User> findUsers(String username, int pageNo, int pageSize) {
        Query query = Query.newInstance().addParam("username", username).addParam("pageNo", pageNo)
                .addParam("pageSize", pageSize).addParam("search", "blur");
        return getUserPageFromRemote(query);
    }
    
    @Override
    public User getUser(String username) {
        if (getCachedUserMap().containsKey(username)) {
            return getCachedUserMap().get(username);
        }
        reload();
        return getCachedUserMap().get(username);
    }
    
    @Override
    public List<String> findUserNames(String username) {
        Query query = Query.newInstance().addParam("username", username);
        try {
            HttpRestResult<String> httpResult = nacosRestTemplate.get(
                    buildRemoteUserUrlPath(AuthConstants.USER_PATH + "/search"),
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
    public void createUser(String username, String password, boolean encode) {
        validateUserCredentials(username, password);
        if (AuthConstants.DEFAULT_USER.equals(username)) {
            doCreateAdminUser(password);
            return;
        }
        // ignore encode = true, let nacos server do encode
        Query query = Query.newInstance().addParam("username", username);
        Map<String, String> body = Map.of("password", password);
        try {
            HttpRestResult<String> result = nacosRestTemplate.postForm(buildRemoteUserUrlPath(AuthConstants.USER_PATH),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), query, body, String.class);
            RemoteServerUtil.singleCheckResult(result);
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    private void doCreateAdminUser(String password) {
        Map<String, String> body = Map.of("password", password);
        try {
            HttpRestResult<String> result = nacosRestTemplate.postForm(
                    buildRemoteUserUrlPath(AuthConstants.USER_PATH + "/admin"),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), Query.newInstance(), body, String.class);
            RemoteServerUtil.singleCheckResult(result);
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    @Override
    public void deleteUser(String username) {
        Query query = Query.newInstance().addParam("username", username);
        try {
            HttpRestResult<String> result = nacosRestTemplate.delete(buildRemoteUserUrlPath(AuthConstants.USER_PATH),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), query, String.class);
            RemoteServerUtil.singleCheckResult(result);
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
    
    private String buildRemoteUserUrlPath(String apiPath) {
        return RequestUrlConstants.HTTP_PREFIX + RemoteServerUtil.getOneNacosServerAddress()
                + RemoteServerUtil.getRemoteServerContextPath() + apiPath;
    }
    
    private Page<User> getUserPageFromRemote(Query query) {
        try {
            HttpRestResult<String> httpResult = nacosRestTemplate.get(
                    buildRemoteUserUrlPath(AuthConstants.USER_PATH + "/list"),
                    RemoteServerUtil.buildServerRemoteHeader(authConfigs), query, String.class);
            RemoteServerUtil.singleCheckResult(httpResult);
            Result<Page<User>> result = JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
            return result.getData();
        } catch (NacosException e) {
            throw new NacosRuntimeException(e.getErrCode(), e.getErrMsg());
        } catch (Exception unpectedException) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, unpectedException.getMessage());
        }
    }
}
