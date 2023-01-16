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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.jwt.NacosJwtParser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JWT token manager.
 *
 * @author wfnuser
 * @author nkorange
 */
@Component
public class JwtTokenManager extends Subscriber<ServerConfigChangeEvent> {
    
    @Deprecated
    private static final String AUTHORITIES_KEY = "auth";
    
    /**
     * Token validity time(seconds).
     */
    private volatile long tokenValidityInSeconds;
    
    private volatile NacosJwtParser jwtParser;
    
    public JwtTokenManager() {
        NotifyCenter.registerSubscriber(this);
        processProperties();
    }
    
    private void processProperties() {
        this.tokenValidityInSeconds = EnvUtil.getProperty(AuthConstants.TOKEN_EXPIRE_SECONDS, Long.class,
                AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS);
        
        String encodedSecretKey = EnvUtil.getProperty(AuthConstants.TOKEN_SECRET_KEY,
                AuthConstants.DEFAULT_TOKEN_SECRET_KEY);
        try {
            this.jwtParser = new NacosJwtParser(encodedSecretKey);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "the length of  must great than or equal 32 bytes; And the secret key  must be encoded by base64",
                    e);
        }
        
    }
    
    /**
     * Create token.
     *
     * @param authentication auth info
     * @return token
     */
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
        return jwtParser.jwtBuilder().setUserName(userName).setExpiredTime(this.tokenValidityInSeconds).compact();
    }
    
    /**
     * Get auth Info.
     *
     * @param token token
     * @return auth info
     */
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
        jwtParser.parse(token);
    }
    
    public long getTokenValidityInSeconds() {
        return tokenValidityInSeconds;
    }
    
    @Override
    public void onEvent(ServerConfigChangeEvent event) {
        processProperties();
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServerConfigChangeEvent.class;
    }
}
