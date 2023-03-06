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

package com.alibaba.nacos.plugin.auth.constant;

/**
 * All the constants.
 *
 * @author onew
 */
public class Constants {
    
    public static class Auth {
        
        public static final String NACOS_CORE_AUTH_ENABLED = "nacos.core.auth.enabled";
        
        public static final String NACOS_CORE_AUTH_SYSTEM_TYPE = "nacos.core.auth.system.type";
        
        public static final String NACOS_CORE_AUTH_CACHING_ENABLED = "nacos.core.auth.caching.enabled";
        
        public static final String NACOS_CORE_AUTH_SERVER_IDENTITY_KEY = "nacos.core.auth.server.identity.key";
        
        public static final String NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE = "nacos.core.auth.server.identity.value";
        
        public static final String NACOS_CORE_AUTH_ENABLE_USER_AGENT_AUTH_WHITE = "nacos.core.auth.enable.userAgentAuthWhite";
        
    }
    
    public static class Resource {
        
        public static final String SPLITTER = ":";
        
        public static final String ANY = "*";
        
        public static final String ACTION = "action";
        
        public static final String REQUEST_CLASS = "requestClass";
    }
    
    public static class Identity {
        
        public static final String IDENTITY_ID = "identity_id";
        
        public static final String X_REAL_IP = "X-Real-IP";
        
        public static final String REMOTE_IP = "remote_ip";
        
        public static final String IDENTITY_CONTEXT = "identity_context";
    }
}
