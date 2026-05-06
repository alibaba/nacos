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
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.springframework.security.core.Authentication;

/**
 * Token Manager Interface.
 *
 * @author majorhe
 */
public interface TokenManager {
    
    /**
     * Create token.
     *
     * @param authentication auth info
     * @return token
     * @throws AccessException access exception
     */
    String createToken(Authentication authentication) throws AccessException;
    
    /**
     * Create token.
     *
     * @param userName auth info
     * @return token
     * @throws AccessException access exception
     */
    String createToken(String userName) throws AccessException;
    
    /**
     * Get auth Info.
     *
     * @param token token
     * @return auth info
     * @throws AccessException access exception
     */
    Authentication getAuthentication(String token) throws AccessException;
    
    /**
     * validate token.
     *
     * @param token token
     * @throws AccessException access exception
     */
    void validateToken(String token) throws AccessException;
    
    /**
     * parse token.
     *
     * @param token token
     * @return nacos user object
     * @throws AccessException access exception
     */
    NacosUser parseToken(String token) throws AccessException;
    
    /**
     * validate token.
     *
     * @return  token validity in seconds
     * @throws AccessException access exception
     */
    long getTokenValidityInSeconds() throws AccessException;
    
    /**
     * validate token.
     *
     * @param token token
     * @return token ttl in seconds
     * @throws AccessException access exception
     */
    long getTokenTtlInSeconds(String token) throws AccessException;
    
}
