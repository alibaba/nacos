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

package com.alibaba.nacos.plugin.auth.impl.token.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfigProvider;
import com.alibaba.nacos.plugin.auth.impl.jwt.NacosJwtParser;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JWT token manager.
 *
 * @author wfnuser
 * @author nkorange
 */
public class JwtTokenManager implements TokenManager {
    
    private static final String AUTH_DISABLED_TOKEN = "AUTH_DISABLED";
    
    private final NacosAuthPluginConfigProvider configProvider;
    
    private final NacosJwtParser jwtParser;
    
    public JwtTokenManager(NacosAuthPluginConfigProvider configProvider) {
        this.configProvider = configProvider;
        String encodedSecretKey = configProvider.getConfig().getTokenSecretKey();
        if (StringUtils.isBlank(encodedSecretKey)) {
            jwtParser = null;
            return;
        }
        jwtParser = new NacosJwtParser(encodedSecretKey);
    }
    
    /**
     * Create token.
     *
     * @param authentication auth info
     * @return token
     */
    @Deprecated
    public String createToken(Authentication authentication) {
        return createToken(authentication.getName());
    }
    
    /**
     * Create token.
     *
     * @param userName auth info
     * @return token
     */
    public String createToken(String userName) {
        boolean authEnabled = NacosAuthConfigHolder.getInstance().isAnyAuthEnabled();
        if (!authEnabled && jwtParser == null) {
            return AUTH_DISABLED_TOKEN;
        }
        if (authEnabled) {
            checkJwtParser();
        }
        return jwtParser.jwtBuilder().setUserName(userName)
            .setExpiredTime(getTokenValidityInSeconds()).compact();
    }
    
    /**
     * Get auth Info.
     *
     * @param token token
     * @return auth info
     */
    @Deprecated
    public Authentication getAuthentication(String token) throws AccessException {
        NacosUser nacosUser = parseToken(token);
        List<GrantedAuthority> authorities =
            AuthorityUtils.commaSeparatedStringToAuthorityList(StringUtils.EMPTY);
        User principal = new User(nacosUser.getUserName(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }
    
    @Override
    public void validateToken(String token) throws AccessException {
        parseToken(token);
    }
    
    @Override
    public NacosUser parseToken(String token) throws AccessException {
        checkJwtParser();
        return jwtParser.parse(token);
    }
    
    @Override
    public long getTokenValidityInSeconds() {
        return configProvider.getConfig().getTokenExpireSeconds();
    }
    
    @Override
    public long getTokenTtlInSeconds(String token) throws AccessException {
        if (!NacosAuthConfigHolder.getInstance().isAnyAuthEnabled()) {
            return getTokenValidityInSeconds();
        }
        checkJwtParser();
        return jwtParser.getExpireTimeInSeconds(token)
            - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }
    
    public long getExpiredTimeInSeconds(String token) throws AccessException {
        if (!NacosAuthConfigHolder.getInstance().isAnyAuthEnabled()) {
            return getTokenValidityInSeconds();
        }
        checkJwtParser();
        return jwtParser.getExpireTimeInSeconds(token);
    }
    
    private void checkJwtParser() {
        if (jwtParser == null) {
            throw new NacosRuntimeException(NacosException.INVALID_PARAM,
                "Please config `nacos.plugin.auth.nacos.token.secret.key`, detail see "
                    + "https://nacos.io/docs/latest/manual/admin/auth/");
        }
    }
}
