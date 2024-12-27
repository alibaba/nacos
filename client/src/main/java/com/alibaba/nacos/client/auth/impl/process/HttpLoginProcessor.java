/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.auth.impl.process;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.auth.impl.NacosAuthLoginConstant;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.alibaba.nacos.client.naming.utils.UtilAndComs.webContext;
import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTPS_PREFIX;
import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;

/**
 * Login processor for Http.
 *
 * @author Nacos
 */
public class HttpLoginProcessor implements LoginProcessor {
    
    private static final Logger SECURITY_LOGGER = LoggerFactory.getLogger(HttpLoginProcessor.class);
    
    private static final String LOGIN_URL = "/v1/auth/users/login";
    
    private final NacosRestTemplate nacosRestTemplate;
    
    public HttpLoginProcessor(NacosRestTemplate nacosRestTemplate) {
        this.nacosRestTemplate = nacosRestTemplate;
    }
    
    @Override
    public LoginIdentityContext getResponse(Properties properties) {
        
        String contextPath = ContextPathUtil.normalizeContextPath(
                properties.getProperty(PropertyKeyConst.CONTEXT_PATH, webContext));
        String server = properties.getProperty(NacosAuthLoginConstant.SERVER, StringUtils.EMPTY);
        
        if (!server.startsWith(HTTPS_PREFIX) && !server.startsWith(HTTP_PREFIX)) {
            if (!InternetAddressUtil.containsPort(server)) {
                server = server + InternetAddressUtil.IP_PORT_SPLITER + ParamUtil.getDefaultServerPort();
            }
            server = HTTP_PREFIX + server;
        }
        
        String url = server + contextPath + LOGIN_URL;
        
        Map<String, String> params = new HashMap<>(2);
        Map<String, String> bodyMap = new HashMap<>(2);
        params.put(PropertyKeyConst.USERNAME, properties.getProperty(PropertyKeyConst.USERNAME, StringUtils.EMPTY));
        bodyMap.put(PropertyKeyConst.PASSWORD, properties.getProperty(PropertyKeyConst.PASSWORD, StringUtils.EMPTY));
        try {
            HttpRestResult<String> restResult = nacosRestTemplate.postForm(url, Header.EMPTY,
                    Query.newInstance().initParams(params), bodyMap, String.class);
            if (!restResult.ok()) {
                SECURITY_LOGGER.error("login failed: {}", JacksonUtils.toJson(restResult));
                return null;
            }
            JsonNode obj = JacksonUtils.toObj(restResult.getData());
            
            LoginIdentityContext loginIdentityContext = new LoginIdentityContext();
            
            if (obj.has(Constants.ACCESS_TOKEN)) {
                loginIdentityContext.setParameter(NacosAuthLoginConstant.ACCESSTOKEN,
                        obj.get(Constants.ACCESS_TOKEN).asText());
                loginIdentityContext.setParameter(NacosAuthLoginConstant.TOKENTTL,
                        obj.get(Constants.TOKEN_TTL).asText());
            } else {
                SECURITY_LOGGER.info("[NacosClientAuthServiceImpl] ACCESS_TOKEN is empty from response");
            }
            return loginIdentityContext;
        } catch (Exception e) {
            Map<String, String> newBodyMap = new HashMap<>(bodyMap);
            newBodyMap.put(PropertyKeyConst.PASSWORD,
                    ParamUtil.desensitiseParameter(bodyMap.get(PropertyKeyConst.PASSWORD)));
            SECURITY_LOGGER.error("[NacosClientAuthServiceImpl] login http request failed"
                    + " url: {}, params: {}, bodyMap: {}, errorMsg: {}", url, params, newBodyMap, e.getMessage());
            return null;
        }
    }
    
}
