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

package com.alibaba.nacos.console.security.nacos;

/**
 * Spring security config.
 *
 * @author Nacos
 */
public class NacosAuthConfig {
    
    public static final String AUTHORIZATION_HEADER = "Authorization";
    
    public static final String SECURITY_IGNORE_URLS_SPILT_CHAR = ",";
    
    public static final String LOGIN_ENTRY_POINT = "/v1/auth/login";
    
    public static final String TOKEN_BASED_AUTH_ENTRY_POINT = "/v1/auth/**";
    
    public static final String TOKEN_PREFIX = "Bearer ";
    
    public static final String CONSOLE_RESOURCE_NAME_PREFIX = "console/";
    
    public static final String UPDATE_PASSWORD_ENTRY_POINT = CONSOLE_RESOURCE_NAME_PREFIX + "user/password";
    
    private static final String DEFAULT_ALL_PATH_PATTERN = "/**";
    
    private static final String PROPERTY_IGNORE_URLS = "nacos.security.ignore.urls";
}
