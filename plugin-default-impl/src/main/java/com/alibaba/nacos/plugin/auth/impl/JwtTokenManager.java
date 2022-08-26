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

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * JWT token manager.
 *
 * @author wfnuser
 * @author nkorange
 */
@Component
public class JwtTokenManager {
    
    private static final String AUTHORITIES_KEY = "auth";
    
    private final AuthConfigs authConfigs;
    
    /**
     * secret key.
     */
    private String secretKey;
    
    /**
     * secret key byte array.
     */
    private byte[] secretKeyBytes;
    
    /**
     * Token validity time(seconds).
     */
    private long tokenValidityInSeconds;

    private JwtParser jwtParser;
    
    public JwtTokenManager(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }
    
    /**
     * init tokenValidityInSeconds and secretKey properties.
     */
    @PostConstruct
    public void initProperties() {
        Properties properties = authConfigs.getAuthPluginProperties(AuthConstants.AUTH_PLUGIN_TYPE);
        String validitySeconds = properties
                .getProperty(AuthConstants.TOKEN_EXPIRE_SECONDS, AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS);
        tokenValidityInSeconds = Long.parseLong(validitySeconds);
        secretKey = properties.getProperty(AuthConstants.TOKEN_SECRET_KEY, AuthConstants.DEFAULT_TOKEN_SECRET_KEY);
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
        
        long now = System.currentTimeMillis();
        
        Date validity;
        
        validity = new Date(now + this.getTokenValidityInSeconds() * 1000L);
        
        Claims claims = Jwts.claims().setSubject(userName);
        return Jwts.builder().setClaims(claims).setExpiration(validity)
                .signWith(Keys.hmacShaKeyFor(this.getSecretKeyBytes()), SignatureAlgorithm.HS256).compact();
    }
    
    /**
     * Get auth Info.
     *
     * @param token token
     * @return auth info
     */
    public Authentication getAuthentication(String token) {
        if (jwtParser == null) {
            jwtParser = Jwts.parserBuilder().setSigningKey(this.getSecretKeyBytes()).build();
        }
        Claims claims = jwtParser.parseClaimsJws(token).getBody();
        
        List<GrantedAuthority> authorities = AuthorityUtils
                .commaSeparatedStringToAuthorityList((String) claims.get(AUTHORITIES_KEY));
        
        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }
    
    /**
     * validate token.
     *
     * @param token token
     */
    public void validateToken(String token) {
        if (jwtParser == null) {
            jwtParser = Jwts.parserBuilder().setSigningKey(this.getSecretKeyBytes()).build();
        }
        jwtParser.parseClaimsJws(token);
    }
    
    public byte[] getSecretKeyBytes() {
        if (secretKeyBytes == null) {
            try {
                secretKeyBytes = Decoders.BASE64.decode(secretKey);
            } catch (DecodingException e) {
                secretKeyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            }
            
        }
        return secretKeyBytes;
    }
    
    public long getTokenValidityInSeconds() {
        return tokenValidityInSeconds;
    }
}
