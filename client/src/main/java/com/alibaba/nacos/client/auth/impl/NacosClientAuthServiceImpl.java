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

package com.alibaba.nacos.client.auth.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.auth.impl.process.HttpLoginProcessor;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.plugin.auth.spi.client.AbstractClientAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * a ClientAuthService implement.
 *
 * @author wuyfee
 */

public class NacosClientAuthServiceImpl extends AbstractClientAuthService {
    
    private static final Logger SECURITY_LOGGER = LoggerFactory.getLogger(NacosClientAuthServiceImpl.class);
    
    /**
     * TTL of token in seconds.
     */
    private long tokenTtl;
    
    /**
     * Last timestamp refresh security info from server.
     */
    private long lastRefreshTime;
    
    /**
     * time window to refresh security info in seconds.
     */
    private long tokenRefreshWindow;
    
    /**
     * A context to take with when sending request to Nacos server.
     */
    private volatile LoginIdentityContext loginIdentityContext = new LoginIdentityContext();
    
    
    /**
     * Login to servers.
     *
     * @return true if login successfully
     */
    
    @Override
    public Boolean login(Properties properties) {
        try {
            if ((System.currentTimeMillis() - lastRefreshTime) < TimeUnit.SECONDS
                    .toMillis(tokenTtl - tokenRefreshWindow)) {
                return true;
            }
            
            if (StringUtils.isBlank(properties.getProperty(PropertyKeyConst.USERNAME))) {
                lastRefreshTime = System.currentTimeMillis();
                return true;
            }
            
            for (String server : this.serverList) {
                HttpLoginProcessor httpLoginProcessor = new HttpLoginProcessor(nacosRestTemplate);
                properties.setProperty(NacosAuthLoginConstant.SERVER, server);
                LoginIdentityContext identityContext = httpLoginProcessor.getResponse(properties);
                if (identityContext != null) {
                    if (identityContext.getAllKey().contains(NacosAuthLoginConstant.ACCESSTOKEN)) {
                        tokenTtl = Long.parseLong(identityContext.getParameter(NacosAuthLoginConstant.TOKENTTL));
                        tokenRefreshWindow = tokenTtl / 10;
                        lastRefreshTime = System.currentTimeMillis();

                        LoginIdentityContext newCtx = new LoginIdentityContext();
                        newCtx.setParameter(NacosAuthLoginConstant.ACCESSTOKEN,
                                identityContext.getParameter(NacosAuthLoginConstant.ACCESSTOKEN));
                        this.loginIdentityContext = newCtx;
                    }
                    return true;
                }
            }
        } catch (Throwable throwable) {
            SECURITY_LOGGER.warn("[SecurityProxy] login failed, error: ", throwable);
            return false;
        }
        return false;
    }
    
    @Override
    public LoginIdentityContext getLoginIdentityContext(RequestResource resource) {
        return this.loginIdentityContext;
    }
    
    @Override
    public void shutdown() throws NacosException {
    
    }
}
