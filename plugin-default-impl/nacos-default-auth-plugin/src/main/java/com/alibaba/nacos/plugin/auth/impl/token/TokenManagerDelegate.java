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

package com.alibaba.nacos.plugin.auth.impl.token;

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.token.impl.CachedJwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.token.impl.JwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * token manager delegate.
 *
 * @author majorhe
 */
@Component
public class TokenManagerDelegate implements TokenManager {
    
    public static final String NACOS_AUTH_TOKEN_CACHING_ENABLED = "nacos.core.auth.plugin.nacos.token.cache.enable";
    
    private boolean tokenCacheEnabled = false;
    
    @Autowired
    private JwtTokenManager jwtTokenManager;
    
    @Autowired
    private CachedJwtTokenManager cachedJwtTokenManager;
    
    @PostConstruct
    public void init() {
        tokenCacheEnabled = EnvUtil.getProperty(NACOS_AUTH_TOKEN_CACHING_ENABLED, Boolean.class, false);
    }
    
    private TokenManager getExecuteTokenManager() {
        return tokenCacheEnabled ? cachedJwtTokenManager : jwtTokenManager;
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
