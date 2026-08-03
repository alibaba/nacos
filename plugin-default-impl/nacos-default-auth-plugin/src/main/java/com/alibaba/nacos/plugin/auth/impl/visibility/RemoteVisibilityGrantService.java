/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.visibility;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.constant.RequestUrlConstants;
import com.alibaba.nacos.common.http.DefaultHttpClientFactory;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.utils.RemoteServerUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote visibility grant service for standalone Console deployment.
 *
 * @author Zhengcy05
 */
public class RemoteVisibilityGrantService implements VisibilityGrantService {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(RemoteVisibilityGrantService.class);
    
    private final NacosRestTemplate nacosRestTemplate;
    
    public RemoteVisibilityGrantService() {
        this(new DefaultHttpClientFactory(LOGGER).createNacosRestTemplate());
    }
    
    public RemoteVisibilityGrantService(NacosRestTemplate nacosRestTemplate) {
        this.nacosRestTemplate = nacosRestTemplate;
    }
    
    @Override
    public void grant(String namespaceId, String resourceType, String resourceName, String username,
        String action) throws NacosException {
        Map<String, String> body = new LinkedHashMap<>(4);
        body.put("resourceType", resourceType);
        body.put("resourceName", resourceName);
        body.put("username", username);
        body.put("action", action);
        try {
            HttpRestResult<String> result = nacosRestTemplate.postForm(buildRemoteUrl(),
                buildForwardedIdentityHeader(), buildForwardedAccessTokenQuery(namespaceId), body,
                String.class);
            RemoteServerUtil.singleCheckResult(result);
        } catch (NacosException e) {
            throw e;
        } catch (Exception unexpectedException) {
            throw new NacosException(NacosException.SERVER_ERROR,
                unexpectedException.getMessage());
        }
    }
    
    @Override
    public void revoke(String namespaceId, String resourceType, String resourceName,
        String username, String action) throws NacosException {
        Query query = buildForwardedAccessTokenQuery(namespaceId).addParam("resourceType",
            resourceType).addParam("resourceName", resourceName).addParam("username", username)
            .addParam("action", action);
        try {
            HttpRestResult<String> result = nacosRestTemplate.delete(buildRemoteUrl(),
                buildForwardedIdentityHeader(), query, String.class);
            RemoteServerUtil.singleCheckResult(result);
        } catch (NacosException e) {
            throw e;
        } catch (Exception unexpectedException) {
            throw new NacosException(NacosException.SERVER_ERROR,
                unexpectedException.getMessage());
        }
    }
    
    @Override
    public List<String> findAuthorizedResourceNames(String username, String namespaceId,
        String resourceType, String action) {
        return Collections.emptyList();
    }
    
    private String buildRemoteUrl() {
        return RequestUrlConstants.HTTP_PREFIX + RemoteServerUtil.getOneNacosServerAddress()
            + RemoteServerUtil.getRemoteServerContextPath() + AuthConstants.VISIBILITY_PATH;
    }
    
    private Header buildForwardedIdentityHeader() {
        Header header = Header.newInstance();
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return header;
        }
        String authorization = request.getHeader(AuthConstants.AUTHORIZATION_HEADER);
        if (StringUtils.isNotBlank(authorization)) {
            header.addParam(AuthConstants.AUTHORIZATION_HEADER, authorization);
        }
        return header;
    }
    
    private Query buildForwardedAccessTokenQuery(String namespaceId) {
        Query query = Query.newInstance().addParam("namespaceId", namespaceId);
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return query;
        }
        String accessToken = request.getParameter(Constants.ACCESS_TOKEN);
        if (StringUtils.isNotBlank(accessToken)) {
            query.addParam(Constants.ACCESS_TOKEN, accessToken);
        }
        return query;
    }
    
    private HttpServletRequest getCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        return servletRequestAttributes.getRequest();
    }
}
