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

package com.alibaba.nacos.plugin.auth.impl.token;

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfigProvider;
import com.alibaba.nacos.plugin.auth.impl.token.impl.CachedJwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.token.impl.JwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;

import java.util.Objects;

/**
 * Stable token manager facade whose delegate is selected by plugin configuration.
 *
 * @author majorhe
 */
public class TokenManagerDelegate implements TokenManager {
    
    private final NacosAuthPluginConfigProvider configProvider;
    
    private volatile JwtTokenManager tokenManager;
    
    private volatile CachedJwtTokenManager cachedTokenManager;
    
    private NacosAuthPluginConfig lastAppliedConfig;
    
    public TokenManagerDelegate(NacosAuthPluginConfigProvider configProvider) {
        this.configProvider = configProvider;
    }
    
    private TokenManager getExecuteTokenManager() {
        JwtTokenManager direct = tokenManager;
        CachedJwtTokenManager cached = cachedTokenManager;
        if (direct == null || cached == null) {
            throw new IllegalStateException("Nacos auth plugin has not been initialized");
        }
        return configProvider.getConfig().isTokenCacheEnabled() ? cached : direct;
    }
    
    /**
     * Initialize token managers once and clear cached state after relevant config changes.
     */
    public synchronized void applyTokenConfig() {
        NacosAuthPluginConfig current = configProvider.getConfig();
        if (lastAppliedConfig != null && !Objects.equals(lastAppliedConfig.getTokenSecretKey(),
            current.getTokenSecretKey())) {
            throw new IllegalArgumentException("Token secret key change requires restart");
        }
        if (tokenManager == null) {
            JwtTokenManager direct = new JwtTokenManager(configProvider);
            CachedJwtTokenManager cached = new CachedJwtTokenManager(direct, configProvider);
            tokenManager = direct;
            cachedTokenManager = cached;
        } else if (shouldClearCache(current)) {
            cachedTokenManager.clear();
        }
        lastAppliedConfig = current;
    }
    
    private boolean shouldClearCache(NacosAuthPluginConfig current) {
        return lastAppliedConfig.getTokenExpireSeconds() != current.getTokenExpireSeconds()
            || lastAppliedConfig.isTokenCacheEnabled() != current.isTokenCacheEnabled();
    }
    
    /**
     * Clean expired tokens when the active delegate has a token cache.
     */
    @Scheduled(initialDelay = 30000, fixedDelay = 60000)
    public void cleanExpiredToken() {
        CachedJwtTokenManager current = cachedTokenManager;
        if (current != null && configProvider.getConfig().isTokenCacheEnabled()) {
            current.cleanExpiredToken();
        }
    }
    
    @Override
    public String createToken(Authentication authentication) throws AccessException {
        return getExecuteTokenManager().createToken(authentication);
    }
    
    @Override
    public String createToken(String userName) throws AccessException {
        return getExecuteTokenManager().createToken(userName);
    }
    
    @Override
    public Authentication getAuthentication(String token) throws AccessException {
        return getExecuteTokenManager().getAuthentication(token);
    }
    
    @Override
    public void validateToken(String token) throws AccessException {
        getExecuteTokenManager().validateToken(token);
    }
    
    @Override
    public NacosUser parseToken(String token) throws AccessException {
        return getExecuteTokenManager().parseToken(token);
    }
    
    @Override
    public long getTokenValidityInSeconds() throws AccessException {
        return getExecuteTokenManager().getTokenValidityInSeconds();
    }
    
    @Override
    public long getTokenTtlInSeconds(String token) throws AccessException {
        return getExecuteTokenManager().getTokenTtlInSeconds(token);
    }
}
