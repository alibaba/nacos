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
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JWT token manager.
 *
 * @author wfnuser
 * @author nkorange
 */
@Component
public class JwtTokenManager extends Subscriber<ServerConfigChangeEvent> {
    
    private static final String AUTHORITIES_KEY = "auth";
    
    /**
     * Token validity time(seconds).
     */
    private volatile long tokenValidityInSeconds;
    
    private volatile JwtParser jwtParser;
    
    private volatile SecretKey secretKey;
    
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
            this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(encodedSecretKey));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "the length of  must great than or equal 32 bytes; And the secret key  must be encoded by base64",
                    e);
        }
        
        this.jwtParser = Jwts.parserBuilder().setSigningKey(secretKey).build();
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
        
        Date validity = new Date(
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(this.getTokenValidityInSeconds()));
        
        Claims claims = Jwts.claims().setSubject(userName);
        return Jwts.builder().setClaims(claims).setExpiration(validity).signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * Get auth Info.
     *
     * @param token token
     * @return auth info
     */
    public Authentication getAuthentication(String token) {
        Claims claims = jwtParser.parseClaimsJws(token).getBody();
        
        List<GrantedAuthority> authorities = AuthorityUtils.commaSeparatedStringToAuthorityList(
                (String) claims.get(AUTHORITIES_KEY));
        
        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }
    
    /**
     * validate token.
     *
     * @param token token
     */
    public void validateToken(String token) {
        jwtParser.parseClaimsJws(token);
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
