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

package com.alibaba.nacos.plugin.auth.impl.oidc.identity;

import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.token.JwtTokenValidator;
import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Maps OIDC token claims to Nacos user representation.
 *
 * @author WangzJi
 */
public class OidcUserMapper {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcUserMapper.class);
    
    private static volatile OidcUserMapper instance;
    
    private final OidcAuthConfig config;
    
    private final JwtTokenValidator tokenValidator;
    
    private OidcUserMapper() {
        this.config = OidcAuthConfig.getInstance();
        this.tokenValidator = JwtTokenValidator.getInstance();
    }
    
    /**
     * Get singleton instance.
     *
     * @return OidcUserMapper instance
     */
    public static OidcUserMapper getInstance() {
        if (instance == null) {
            synchronized (OidcUserMapper.class) {
                if (instance == null) {
                    instance = new OidcUserMapper();
                }
            }
        }
        return instance;
    }
    
    /**
     * Map JWT claims to OidcUser.
     *
     * @param claims JWT claims from validated token
     * @return OidcUser instance
     */
    public OidcUser mapToUser(JWTClaimsSet claims) {
        String username = tokenValidator.extractUsername(claims);
        List<String> roles = tokenValidator.extractRoles(claims);
        boolean isAdmin = tokenValidator.isAdmin(claims);
        
        OidcUser user = new OidcUser();
        user.setUsername(username);
        user.setSubject(claims.getSubject());
        user.setRoles(roles);
        user.setGlobalAdmin(isAdmin);
        
        // Extract additional claims if present
        user.setEmail((String) claims.getClaim("email"));
        user.setName((String) claims.getClaim("name"));
        user.setIssuer(claims.getIssuer());
        
        LOGGER.debug("Mapped OIDC user: username={}, roles={}, isAdmin={}",
            username, roles, isAdmin);
        
        return user;
    }
    
    /**
     * OIDC user representation.
     */
    @SuppressWarnings("PMD")
    public static class OidcUser {
        
        private String username;
        
        private String subject;
        
        private String email;
        
        private String name;
        
        private String issuer;
        
        private List<String> roles;
        
        private boolean globalAdmin;
        
        private String token;
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getSubject() {
            return subject;
        }
        
        public void setSubject(String subject) {
            this.subject = subject;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getIssuer() {
            return issuer;
        }
        
        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
        
        public List<String> getRoles() {
            return roles;
        }
        
        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
        
        public boolean isGlobalAdmin() {
            return globalAdmin;
        }
        
        public void setGlobalAdmin(boolean globalAdmin) {
            this.globalAdmin = globalAdmin;
        }
        
        public String getToken() {
            return token;
        }
        
        public void setToken(String token) {
            this.token = token;
        }
        
        @Override
        public String toString() {
            return "OidcUser{"
                + "username='" + username + '\''
                + ", subject='" + subject + '\''
                + ", roles=" + roles
                + ", globalAdmin=" + globalAdmin
                + '}';
        }
    }
}
