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

package com.alibaba.nacos.client.auth.ram.injector;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.client.auth.ram.RamContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.client.config.impl.ConfigHttpClientManager;
import com.alibaba.nacos.client.auth.ram.utils.SpasAdapter;
import com.alibaba.nacos.client.auth.ram.identify.StsConfig;
import com.alibaba.nacos.client.auth.ram.identify.StsCredential;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Resource Injector for naming module.
 *
 * @author xiweng.yy
 */
public class ConfigResourceInjector extends AbstractResourceInjector {
    
    private static final Logger LOGGER = LogUtils.logger(ConfigResourceInjector.class);
    
    private static final String SECURITY_TOKEN_HEADER = "Spas-SecurityToken";
    
    private static final String ACCESS_KEY_HEADER = "Spas-AccessKey";
    
    private static final String DEFAULT_RESOURCE = "";
    
    private StsCredential stsCredential;
    
    @Override
    public void doInject(RequestResource resource, RamContext context, LoginIdentityContext result) {
        String accessKey = context.getAccessKey();
        String secretKey = context.getSecretKey();
        // STS 临时凭证鉴权的优先级高于 AK/SK 鉴权
        if (StsConfig.getInstance().isStsOn()) {
            StsCredential stsCredential = getStsCredential();
            accessKey = stsCredential.getAccessKeyId();
            secretKey = stsCredential.getAccessKeySecret();
            result.setParameter(SECURITY_TOKEN_HEADER, stsCredential.getSecurityToken());
        }
        
        if (StringUtils.isNotEmpty(accessKey) && StringUtils.isNotBlank(secretKey)) {
            result.setParameter(ACCESS_KEY_HEADER, accessKey);
        }
        Map<String, String> signHeaders = SpasAdapter
                .getSignHeaders(getResource(resource.getNamespace(), resource.getGroup()), secretKey);
        result.setParameters(signHeaders);
    }
    
    private StsCredential getStsCredential() {
        boolean cacheSecurityCredentials = StsConfig.getInstance().isCacheSecurityCredentials();
        if (cacheSecurityCredentials && stsCredential != null) {
            long currentTime = System.currentTimeMillis();
            long expirationTime = stsCredential.getExpiration().getTime();
            int timeToRefreshInMillisecond = StsConfig.getInstance().getTimeToRefreshInMillisecond();
            if (expirationTime - currentTime > timeToRefreshInMillisecond) {
                return stsCredential;
            }
        }
        String stsResponse = getStsResponse();
        stsCredential = JacksonUtils.toObj(stsResponse, new TypeReference<StsCredential>() {
        });
        LOGGER.info("[getSTSCredential] code:{}, accessKeyId:{}, lastUpdated:{}, expiration:{}",
                stsCredential.getCode(), stsCredential.getAccessKeyId(), stsCredential.getLastUpdated(),
                stsCredential.getExpiration());
        return stsCredential;
    }
    
    private static String getStsResponse() {
        String securityCredentials = StsConfig.getInstance().getSecurityCredentials();
        if (securityCredentials != null) {
            return securityCredentials;
        }
        String securityCredentialsUrl = StsConfig.getInstance().getSecurityCredentialsUrl();
        try {
            HttpRestResult<String> result = ConfigHttpClientManager.getInstance().getNacosRestTemplate()
                    .get(securityCredentialsUrl, Header.EMPTY, Query.EMPTY, String.class);
            
            if (!result.ok()) {
                LOGGER.error(
                        "can not get security credentials, securityCredentialsUrl: {}, responseCode: {}, response: {}",
                        securityCredentialsUrl, result.getCode(), result.getMessage());
                throw new NacosRuntimeException(NacosException.SERVER_ERROR,
                        "can not get security credentials, responseCode: " + result.getCode() + ", response: " + result
                                .getMessage());
            }
            return result.getData();
        } catch (Exception e) {
            LOGGER.error("can not get security credentials", e);
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
    }
    
    private String getResource(String tenant, String group) {
        if (StringUtils.isNotBlank(tenant) && StringUtils.isNotBlank(group)) {
            return tenant + "+" + group;
        }
        if (StringUtils.isNotBlank(group)) {
            return group;
        }
        if (StringUtils.isNotBlank(tenant)) {
            return tenant;
        }
        return DEFAULT_RESOURCE;
    }
}
