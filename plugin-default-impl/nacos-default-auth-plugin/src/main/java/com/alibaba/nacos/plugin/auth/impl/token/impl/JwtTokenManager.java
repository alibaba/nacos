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

package com.alibaba.nacos.plugin.auth.impl.token.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.jwt.NacosJwtParser;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JWT token manager.
 *
 * @author wfnuser
 * @author nkorange
 */
@Component
public class JwtTokenManager extends Subscriber<ServerConfigChangeEvent> implements TokenManager {
    
    private static final String AUTH_DISABLED_TOKEN = "AUTH_DISABLED";
    
    /**
     * Token validity time(seconds).
     */
    private volatile long tokenValidityInSeconds;
    
    private volatile NacosJwtParser jwtParser;
    
    private final AuthConfigs authConfigs;
    
    public JwtTokenManager(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
        NotifyCenter.registerSubscriber(this);
        processProperties();
    }
    
    private void processProperties() {
        this.tokenValidityInSeconds = EnvUtil.getProperty(AuthConstants.TOKEN_EXPIRE_SECONDS, Long.class,
                AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS);
        
        String encodedSecretKey = EnvUtil
                .getProperty(AuthConstants.TOKEN_SECRET_KEY, AuthConstants.DEFAULT_TOKEN_SECRET_KEY);
        try {
            this.jwtParser = new NacosJwtParser(encodedSecretKey);
        } catch (Exception e) {
            this.jwtParser = null;
            if (authConfigs.isAuthEnabled()) {
                throw new IllegalArgumentException(
                        "the length of secret key must great than or equal 32 bytes; And the secret key  must be encoded by base64."
                                + "Please see https://nacos.io/zh-cn/docs/v2/guide/user/auth.html", e);
            }
        }
        
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
        if (!authConfigs.isAuthEnabled()) {
            return AUTH_DISABLED_TOKEN;
        }
        checkJwtParser();
        return jwtParser.jwtBuilder().setUserName(userName).setExpiredTime(this.tokenValidityInSeconds).compact();
    }
    
    /**
     * Get auth Info.
     *
     * @param token token
     * @return auth info
     */
    @Deprecated
    public Authentication getAuthentication(String token) throws AccessException {
        NacosUser nacosUser = jwtParser.parse(token);
        
        List<GrantedAuthority> authorities = AuthorityUtils.commaSeparatedStringToAuthorityList(StringUtils.EMPTY);
        
        User principal = new User(nacosUser.getUserName(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }
    
    /**
     * validate token.
     *
     * @param token token
     */
    public void validateToken(String token) throws AccessException {
        parseToken(token);
    }
    
    public NacosUser parseToken(String token) throws AccessException {
        checkJwtParser();
        return jwtParser.parse(token);
    }
    
    public long getTokenValidityInSeconds() {
        return tokenValidityInSeconds;
    }
    
    @Override
    public long getTokenTtlInSeconds(String token) throws AccessException {
        if (!authConfigs.isAuthEnabled()) {
            return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + tokenValidityInSeconds;
        }
        return jwtParser.getExpireTimeInSeconds(token) - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }
    
    public long getExpiredTimeInSeconds(String token) throws AccessException {
        if (!authConfigs.isAuthEnabled()) {
            return tokenValidityInSeconds;
        }
        return jwtParser.getExpireTimeInSeconds(token);
    }
    
    @Override
    public void onEvent(ServerConfigChangeEvent event) {
        processProperties();
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServerConfigChangeEvent.class;
    }
    
    private void checkJwtParser() {
        if (null == jwtParser) {
            throw new NacosRuntimeException(NacosException.INVALID_PARAM,
                    "Please config `nacos.core.auth.plugin.nacos.token.secret.key`, detail see https://nacos.io/zh-cn/docs/v2/guide/user/auth.html");
        }
    }
}
