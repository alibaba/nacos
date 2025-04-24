/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oidc;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetails;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Open ID Connect User and Token Service.
 *
 * @author Roiocam
 */
@SuppressWarnings("checkstyle:abbreviationaswordinname")
@Service
public class OIDCService {
    
    private final NacosUserDetailsServiceImpl userDetailsService;
    
    private final TokenManagerDelegate jwtTokenManager;
    
    @Autowired
    public OIDCService(NacosUserDetailsServiceImpl userDetailsService, TokenManagerDelegate jwtTokenManager) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenManager = jwtTokenManager;
    }
    
    private NacosUser generateUserFromUsername(String username) throws AccessException {
        String token = jwtTokenManager.createToken(username);
        Authentication authentication = jwtTokenManager.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return new NacosUser(username, token);
    }
    
    private NacosUser getUserFormNacos(String username) throws AccessException {
        if (StringUtils.isBlank(username)) {
            throw new AccessException("user not found!");
        }
        NacosUserDetails nacosUserDetails = (NacosUserDetails) userDetailsService.loadUserByUsername(username);
        if (nacosUserDetails == null) {
            throw new AccessException("user not found!");
        }
        return generateUserFromUsername(nacosUserDetails.getUsername());
    }
    
    public NacosUser getUser(String username) throws AccessException {
        try {
            return getUserFormNacos(username);
        } catch (AccessException | UsernameNotFoundException ignored) {
            if (Loggers.AUTH.isWarnEnabled()) {
                Loggers.AUTH.warn("try login with LDAP, user: {}", username);
            }
        }
        
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(AuthConstants.LDAP_PREFIX + username);
            return generateUserFromUsername(userDetails.getUsername());
        } catch (UsernameNotFoundException ignored) {
            if (Loggers.AUTH.isWarnEnabled()) {
                Loggers.AUTH.warn("try login with OIDC, user: {}", username);
            }
        }
        
        String oidcUsername = AuthConstants.OIDC_PREFIX + username;
        try {
            userDetails = userDetailsService.loadUserByUsername(oidcUsername);
        } catch (UsernameNotFoundException ignored) {
            userDetailsService.createUser(oidcUsername, null);
            User user = new User();
            user.setUsername(oidcUsername);
            user.setPassword(null);
            userDetails = new NacosUserDetails(user);
        } catch (Exception e) {
            Loggers.AUTH.error("[LDAP-LOGIN] failed", e);
            throw new AccessException("user not found");
        }
        
        return generateUserFromUsername(userDetails.getUsername());
    }
    
    public long getTokenTtlInSeconds(String token) {
        try {
            return jwtTokenManager.getTokenTtlInSeconds(token);
        } catch (AccessException e) {
            // shouldn't happen, return default value
            return 18000;
        }
    }
}
