/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.oidc;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.spi.client.AbstractClientAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * OIDC Client Authentication Service implementation.
 *
 * <p>Implements the {@link AbstractClientAuthService} SPI to provide OIDC-based
 * authentication for Nacos clients. Uses the OAuth2 Client Credentials Grant
 * to obtain access tokens from the Identity Provider.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@link #login(Properties)} is called periodically by the framework</li>
 *   <li>On first call with OIDC configured: performs OIDC Discovery and obtains access token</li>
 *   <li>On subsequent calls: checks if token needs refresh and refreshes if needed</li>
 *   <li>{@link #getLoginIdentityContext(RequestResource)} returns the context with accessToken</li>
 * </ol>
 *
 * @author wangzji
 */
public class OidcClientAuthServiceImpl extends AbstractClientAuthService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcClientAuthServiceImpl.class);
    
    private final Object refreshLock = new Object();
    
    private volatile OidcClientContext context;
    
    private volatile OidcTokenHolder tokenHolder;
    
    private volatile LoginIdentityContext loginIdentityContext = new LoginIdentityContext();
    
    /**
     * Whether OIDC has been determined to be unconfigured.
     * Once set to true, subsequent login() calls skip OIDC processing.
     */
    private volatile boolean oidcNotConfigured = false;
    
    @Override
    public Boolean login(Properties properties) {
        try {
            // Fast path: if OIDC is already known to be unconfigured, skip
            if (oidcNotConfigured) {
                return true;
            }
            
            // Step 1: Initialize context on first call (double-check locking)
            if (context == null) {
                synchronized (refreshLock) {
                    if (context == null) {
                        OidcClientContext newContext = new OidcClientContext();
                        boolean configured = newContext.init(properties);
                        if (!configured) {
                            oidcNotConfigured = true;
                            LOGGER.debug(
                                "[OIDC-CLIENT] OIDC not configured (missing client-id/client-secret), skipping");
                            return true;
                        }
                        this.tokenHolder = new OidcTokenHolder();
                        this.context = newContext;
                        LOGGER.info("[OIDC-CLIENT] OIDC client configured, client-id: {}",
                            context.getClientId());
                    }
                }
            }
            
            // Step 2: Perform OIDC Discovery if not yet done
            if (!context.isDiscovered()) {
                boolean discoveryResult = context.discover();
                if (!discoveryResult) {
                    LOGGER.warn(
                        "[OIDC-CLIENT] OIDC Discovery failed, will retry on next login cycle");
                    return false;
                }
            }
            
            // Step 3: Fetch or refresh token if needed (double-check locking)
            if (tokenHolder.isExpiredOrNeedRefresh()) {
                synchronized (refreshLock) {
                    if (tokenHolder.isExpiredOrNeedRefresh()) {
                        boolean tokenResult = tokenHolder.fetchToken(context);
                        if (!tokenResult) {
                            LOGGER.warn(
                                "[OIDC-CLIENT] Token fetch failed, will retry on next login cycle");
                            return false;
                        }
                        
                        // Step 4: Update LoginIdentityContext with new access token (dual header)
                        LoginIdentityContext newCtx = new LoginIdentityContext();
                        String token = tokenHolder.getAccessToken();
                        newCtx.setParameter(OidcProtocolConstants.AUTHORIZATION_HEADER,
                            OidcProtocolConstants.BEARER_PREFIX + token);
                        newCtx.setParameter(OidcProtocolConstants.ACCESS_TOKEN_PARAM, token);
                        this.loginIdentityContext = newCtx;
                        
                        LOGGER.debug(
                            "[OIDC-CLIENT] LoginIdentityContext updated with new access token");
                    }
                }
            }
            
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("[OIDC-CLIENT] login failed, error: ", throwable);
            return false;
        }
    }
    
    @Override
    public LoginIdentityContext getLoginIdentityContext(RequestResource resource) {
        return this.loginIdentityContext;
    }
    
    @Override
    public void shutdown() throws NacosException {
        LOGGER.info("[OIDC-CLIENT] Shutting down OIDC client auth service");
    }
}
