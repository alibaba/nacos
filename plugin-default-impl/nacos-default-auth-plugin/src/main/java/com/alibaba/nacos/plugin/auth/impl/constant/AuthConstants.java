/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.constant;

import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;

/**
 * All the constants.
 *
 * @author onew
 */
public class AuthConstants {
    
    public static final String AUTH_PLUGIN_TYPE = "nacos";
    
    public static final String LDAP_AUTH_PLUGIN_TYPE = "ldap";
    
    public static final String GLOBAL_ADMIN_ROLE = "ROLE_ADMIN";
    
    public static final String AUTHORIZATION_HEADER = "Authorization";
    
    public static final String TOKEN_PREFIX = "Bearer ";
    
    public static final String DEFAULT_USER = "nacos";
    
    public static final String PARAM_USERNAME = "username";
    
    public static final String PARAM_PASSWORD = "password";
    
    public static final String CONSOLE_RESOURCE_NAME_PREFIX = "console/";
    
    public static final String UPDATE_PASSWORD_ENTRY_POINT = CONSOLE_RESOURCE_NAME_PREFIX + "user/password";
    
    public static final String NACOS_USER_KEY = "nacosuser";
    
    public static final String TOKEN_SECRET_KEY = "nacos.core.auth.plugin.nacos.token.secret.key";
    
    public static final String DEFAULT_TOKEN_SECRET_KEY = "";
    
    public static final String TOKEN_EXPIRE_SECONDS = "nacos.core.auth.plugin.nacos.token.expire.seconds";
    
    public static final Long DEFAULT_TOKEN_EXPIRE_SECONDS = 18_000L;
    
    public static final String NACOS_CORE_AUTH_LDAP_URL = "nacos.core.auth.ldap.url";
    
    public static final String NACOS_CORE_AUTH_LDAP_BASEDC = "nacos.core.auth.ldap.basedc";
    
    public static final String NACOS_CORE_AUTH_LDAP_TIMEOUT = "nacos.core.auth.ldap.timeout";
    
    public static final String NACOS_CORE_AUTH_LDAP_USERDN = "nacos.core.auth.ldap.userDn";
    
    public static final String NACOS_CORE_AUTH_LDAP_PASSWORD = "nacos.core.auth.ldap.password";
    
    public static final String NACOS_CORE_AUTH_LDAP_FILTER_PREFIX = "nacos.core.auth.ldap.filter.prefix";
    
    public static final String NACOS_CORE_AUTH_CASE_SENSITIVE = "nacos.core.auth.ldap.case.sensitive";
    
    /**
     * LDAP Ignore partial result exception.
     */
    public static final String NACOS_CORE_AUTH_IGNORE_PARTIAL_RESULT_EXCEPTION = "nacos.core.auth.ldap.ignore.partial.result.exception";
    
    @Deprecated
    public static final String LDAP_DEFAULT_PASSWORD = "nacos";
    
    public static final String LDAP_DEFAULT_ENCODED_PASSWORD = PasswordEncoderUtil.encode(LDAP_DEFAULT_PASSWORD);
    
    public static final String LDAP_PREFIX = "LDAP_";
}
