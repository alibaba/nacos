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

package com.alibaba.nacos.client.identify;

/**
 * Identify Constants.
 *
 * @author Nacos
 */
public class IdentifyConstants {
    
    public static final String ACCESS_KEY = "accessKey";
    
    public static final String SECRET_KEY = "secretKey";
    
    public static final String TENANT_ID = "tenantId";
    
    public static final String PROPERTIES_FILENAME = "spas.properties";
    
    public static final String CREDENTIAL_PATH = "/home/admin/.spas_key/";
    
    public static final String CREDENTIAL_DEFAULT = "default";
    
    public static final String DOCKER_CREDENTIAL_PATH = "/etc/instanceInfo";
    
    public static final String DOCKER_ACCESS_KEY = "env_spas_accessKey";
    
    public static final String DOCKER_SECRET_KEY = "env_spas_secretKey";
    
    public static final String DOCKER_TENANT_ID = "ebv_spas_tenantId";
    
    public static final String ENV_ACCESS_KEY = "spas_accessKey";
    
    public static final String ENV_SECRET_KEY = "spas_secretKey";
    
    public static final String ENV_TENANT_ID = "tenant.id";
    
    public static final String NO_APP_NAME = "";
    
    public static final String PROJECT_NAME_PROPERTY = "project.name";
    
    public static final String RAM_ROLE_NAME_PROPERTY = "ram.role.name";
    
    public static final String REFRESH_TIME_PROPERTY = "time.to.refresh.in.millisecond";
    
    public static final String SECURITY_PROPERTY = "security.credentials";
    
    public static final String SECURITY_URL_PROPERTY = "security.credentials.url";
    
    public static final String SECURITY_CACHE_PROPERTY = "cache.security.credentials";
}
