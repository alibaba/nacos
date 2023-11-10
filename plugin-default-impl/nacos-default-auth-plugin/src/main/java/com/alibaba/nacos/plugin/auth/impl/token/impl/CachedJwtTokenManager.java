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

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cached JWT token manager.
 *
 * @author majorhe
 */
@Component
public class CachedJwtTokenManager implements TokenManager {
    
    /**
     * key: token string, value: token entity.
     */
    private volatile Map<String, TokenEntity> tokenMap = new ConcurrentHashMap<>(1024);
    
    /**
     * key: username, value: token entity. cache token created by self.
     */
    private volatile Map<String, TokenEntity> userMap = new ConcurrentHashMap<>(128);
    
    @Autowired
    private JwtTokenManager jwtTokenManager;
    
    @Scheduled(initialDelay = 30000, fixedDelay = 60000)
    private void cleanExpiredToken() {
        List<String> tokens = new ArrayList<>();
        tokenMap.forEach((k, v) -> {
            if (v.getExpiredTimeMills() < System.currentTimeMillis()) {
                tokens.add(k);
            }
        });
        tokens.forEach(e -> tokenMap.remove(e));
        List<String> users = new ArrayList<>();
        userMap.forEach((k, v) -> {
            if (v.getExpiredTimeMills() < System.currentTimeMillis()) {
                users.add(k);
            }
        });
        users.forEach(e -> userMap.remove(e));
    }
    
    @Override
    public String createToken(Authentication authentication) throws AccessException {
        return createToken(authentication.getName());
    }
    
    /**
     * Create token.
     *
     * @param username auth info
     * @return token
     * @throws AccessException access exception
     */
    public String createToken(String username) throws AccessException {
        if (userMap.containsKey(username)) {
            String token = userMap.get(username).getToken();
            long expiredTime = userMap.get(username).getExpiredTimeMills();
            if (!needRefresh(expiredTime)) {
                return token;
            }
        }
        String token = jwtTokenManager.createToken(username);
        NacosUser user = jwtTokenManager.parseToken(token);
        long expiredTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(getTokenValidityInSeconds());
        Authentication authentication = jwtTokenManager.getAuthentication(token);
        TokenEntity model = new TokenEntity(token, username, expiredTime, authentication, user);
        tokenMap.put(token, model);
        userMap.put(username, model);
        return token;
    }
    
    /**
     * Get auth Info.
     *
     * @param token token
     * @return auth info
     * @throws AccessException access exception
     */
    public Authentication getAuthentication(String token) throws AccessException {
        if (!tokenMap.containsKey(token)) {
            return jwtTokenManager.getAuthentication(token);
        }
        return tokenMap.get(token).getAuthentication();
    }
    
    /**
     * validate token.
     *
     * @param token token
     * @throws AccessException access exception
     */
    public void validateToken(String token) throws AccessException {
        if (!tokenMap.containsKey(token)) {
            // jwtTokenManager.validateToken(token) will throw runtime exception if token invalid
            jwtTokenManager.validateToken(token);
            // if token valid
            Authentication authentication = jwtTokenManager.getAuthentication(token);
            String username = authentication.getName();
            if (username == null || username.isEmpty()) {
                return;
            }
            long expiredTime = TimeUnit.SECONDS.toMillis(jwtTokenManager.getExpiredTimeInSeconds(token));
            if (expiredTime <= System.currentTimeMillis()) {
                return;
            }
            NacosUser user = jwtTokenManager.parseToken(token);
            tokenMap.putIfAbsent(token, new TokenEntity(token, username, expiredTime, authentication, user));
        }
    }
    
    @Override
    public NacosUser parseToken(String token) throws AccessException {
        if (!tokenMap.containsKey(token)) {
            Authentication authentication = jwtTokenManager.getAuthentication(token);
            String username = authentication.getName();
            if (username == null || username.isEmpty()) {
                throw new AccessException("invalid token, username is empty");
            }
            long expiredTime = TimeUnit.SECONDS.toMillis(jwtTokenManager.getExpiredTimeInSeconds(token));
            if (expiredTime <= System.currentTimeMillis()) {
                throw new AccessException("expired token");
            }
            NacosUser user = jwtTokenManager.parseToken(token);
            tokenMap.putIfAbsent(token, new TokenEntity(token, username, expiredTime, authentication, user));
            return user;
        }
        return tokenMap.get(token).getNacosUser();
    }
    
    public long getTokenTtlInSeconds(String token) throws AccessException {
        if (tokenMap.containsKey(token)) {
            return TimeUnit.MILLISECONDS.toSeconds(
                    tokenMap.get(token).getExpiredTimeMills() - System.currentTimeMillis());
        }
        return jwtTokenManager.getTokenTtlInSeconds(token);
    }
    
    @Override
    public long getTokenValidityInSeconds() {
        return jwtTokenManager.getTokenValidityInSeconds();
    }
    
    private boolean needRefresh(long expiredTimeMills) {
        long refreshWindowMills = TimeUnit.SECONDS.toMillis(getTokenValidityInSeconds() / 10);
        return System.currentTimeMillis() + refreshWindowMills > expiredTimeMills;
    }
    
    static class TokenEntity {
        
        private String token;
        
        private String userName;
        
        private long expiredTimeMills;
        
        private Authentication authentication;
        
        private NacosUser nacosUser;
        
        public TokenEntity(String token, String userName, long expiredTimeMills, Authentication authentication,
                NacosUser nacosUser) {
            this.token = token;
            this.userName = userName;
            this.expiredTimeMills = expiredTimeMills;
            this.authentication = authentication;
            this.nacosUser = nacosUser;
        }
        
        public String getToken() {
            return token;
        }
        
        public void setToken(String token) {
            this.token = token;
        }
        
        public String getUserName() {
            return userName;
        }
        
        public void setUserName(String userName) {
            this.userName = userName;
        }
        
        public long getExpiredTimeMills() {
            return expiredTimeMills;
        }
        
        public void setExpiredTimeMills(long expiredTimeMills) {
            this.expiredTimeMills = expiredTimeMills;
        }
        
        public Authentication getAuthentication() {
            return authentication;
        }
        
        public void setAuthentication(Authentication authentication) {
            this.authentication = authentication;
        }
        
        public NacosUser getNacosUser() {
            return nacosUser;
        }
        
        public void setNacosUser(NacosUser nacosUser) {
            this.nacosUser = nacosUser;
        }
        
        @Override
        public String toString() {
            return "TokenEntity{" + "token='" + token + '\'' + ", userName='" + userName + '\'' + ", expiredTimeMills="
                    + expiredTimeMills + ", authentication=" + authentication + ", nacosUser=" + nacosUser + '}';
        }
    }
    
}
