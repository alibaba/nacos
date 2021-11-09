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

package com.alibaba.nacos.auth.constant;

import java.io.File;

/**
 * All the constants.
 *
 * @author onew
 */
public class AuthModuleConstants {
    
    public static class Load {
        
        public static final String NOTIFY_CONNECT_TIMEOUT = "notifyConnectTimeout";
        
        public static final String NOTIFY_SOCKET_TIMEOUT = "notifySocketTimeout";
        
        public static final String IS_HEALTH_CHECK = "isHealthCheck";
        
        public static final String MAX_HEALTH_CHECK_FAIL_COUNT = "maxHealthCheckFailCount";
        
        public static final String MAX_CONTENT = "maxContent";
        
        public static final String IS_MANAGE_CAPACITY = "isManageCapacity";
        
        public static final String IS_CAPACITY_LIMIT_CHECK = "isCapacityLimitCheck";
        
        public static final String DEFAULT_CLUSTER_QUOTA = "defaultClusterQuota";
        
        public static final String DEFAULT_GROUP_QUOTA = "defaultGroupQuota";
        
        public static final String DEFAULT_TENANT_QUOTA = "defaultTenantQuota";
        
        public static final String DEFAULT_MAX_SIZE = "defaultMaxSize";
        
        public static final String DEFAULT_MAX_AGGR_COUNT = "defaultMaxAggrCount";
        
        public static final String DEFAULT_MAX_AGGR_SIZE = "defaultMaxAggrSize";
        
        public static final String CORRECT_USAGE_DELAY = "correctUsageDelay";
        
        public static final String INITIAL_EXPANSION_PERCENT = "initialExpansionPercent";
        
        public static final String SPRING_DATASOURCE_PLATFORM = "spring.datasource.platform";
        
        public static final String MYSQL = "mysql";
        
        public static final String EMBEDDED_STORAGE = "embeddedStorage";
    }
    
    public static class Auth {
        
        public static final String GLOBAL_ADMIN_ROLE = "ROLE_ADMIN";
        
        public static final String NACOS_CORE_AUTH_ENABLED = "nacos.core.auth.enabled";
        
        public static final String NACOS_CORE_AUTH_DEFAULT_TOKEN_SECRET_KEY = "nacos.core.auth.default.token.secret.key";
        
        public static final String NACOS_CORE_AUTH_DEFAULT_TOKEN_EXPIRE_SECONDS = "nacos.core.auth.default.token.expire.seconds";
        
        public static final String NACOS_CORE_AUTH_SYSTEM_TYPE = "nacos.core.auth.system.type";
        
        public static final String NACOS_CORE_AUTH_CACHING_ENABLED = "nacos.core.auth.caching.enabled";
        
        public static final String NACOS_CORE_AUTH_SERVER_IDENTITY_KEY = "nacos.core.auth.server.identity.key";
        
        public static final String NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE = "nacos.core.auth.server.identity.value";
        
        public static final String NACOS_CORE_AUTH_ENABLE_USER_AGENT_AUTH_WHITE = "nacos.core.auth.enable.userAgentAuthWhite";
        
        public static final String NACOS_CORE_AUTH_AUTHORITY_KEY = "nacos.core.auth.authorityKey";
    
        public static final String NACOS_IDENTITY_KEY = "identity";
    
        public static final String AUTHORIZATION_HEADER = "Authorization";
    
        public static final String SECURITY_IGNORE_URLS_SPILT_CHAR = ",";
    
        public static final String LOGIN_ENTRY_POINT = "/v1/auth/login";
    
        public static final String TOKEN_BASED_AUTH_ENTRY_POINT = "/v1/auth/**";
    
        public static final String TOKEN_PREFIX = "Bearer ";
    
        public static final String PARAM_PASSWORD = "password";
    
        public static final String CONSOLE_RESOURCE_NAME_PREFIX = "console/";
    
        public static final String UPDATE_PASSWORD_ENTRY_POINT = CONSOLE_RESOURCE_NAME_PREFIX + "user/password";
    
        public static final String DEFAULT_ALL_PATH_PATTERN = "/**";
    
        public static final String PROPERTY_IGNORE_URLS = "nacos.security.ignore.urls";
    }
    
    public static class Ldap {
    
        public static final String FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    
        public static final String TIMEOUT = "com.sun.jndi.ldap.connect.timeout";
    
        public static final String DEFAULT_PASSWORD = "nacos";
    
        public static final String LDAP_PREFIX = "LDAP_";
    
        public static final String DEFAULT_SECURITY_AUTH = "simple";
        
        public static final String NACOS_CORE_AUTH_LDAP_URL = "nacos.core.auth.ldap.url";
        
        public static final String NACOS_CORE_AUTH_LDAP_TIMEOUT = "nacos.core.auth.ldap.timeout";
        
        public static final String NACOS_CORE_AUTH_LDAP_USERDN = "nacos.core.auth.ldap.userdn";
    }
    
    public static class Resource {
        
        public static final String SPLITTER = ":";
        
        public static final String ANY = "*";
    }
    
    public static class DataSource {
        
        public static final String BIND_DB_NAME = "db";
        
        public static final String DB_POOL_CONFIG = "db.pool.config";
    }
    
    public static class ExternalDataSource {
        
        public static final String JDBC_DRIVER_NAME = "com.mysql.cj.jdbc.Driver";
        
        public static final String TEST_QUERY = "SELECT 1";
        
        public static final String QUERYTIMEOUT = "QUERYTIMEOUT";
    }
    
    public static class LocalDataSource {
        
        public static final String JDBC_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
        
        public static final String USER_NAME = "nacos";
        
        public static final String PASSWORD = "nacos";
        
        public static final String DERBY_BASE_DIR = "data" + File.separator + "derby-data";
        
        public static final String SCHEMA_SQL_FILE_LOCAL_PATH = "conf" + File.separator + "schema.sql";
        
        public static final String SCHEMA_SQL_FILE_CLASS_PATH = "META-INF/schema.sql";
        
        public static final String DERBY_SHUTDOWN_ERR_MSG = "Derby system shutdown.";
    }
    
    public static class Log {
        
        public static final String CONFIG_SERVER = "config-server";
        
        public static final String CONFIG_FATAL = "config-fatal";
        
        public static final String CONFIG_PULL = "config-pull";
        
        public static final String CONFIG_PULL_CHECK = "config-pull-check";
        
        public static final String CONFIG_DUMP = "config-dump";
        
        public static final String CONFIG_MEMORY = "config-memory";
        
        public static final String CONFIG_CLIENT_REQUEST = "config-client-request";
        
        public static final String CONFIG_TRACE = "config-trace";
        
        public static final String CONFIG_NOTIFY = "config-notify";
    }
    
    public static class Jwt {
    
        public static final String AUTHORITIES_KEY = "auth";
    
    }
    
}
