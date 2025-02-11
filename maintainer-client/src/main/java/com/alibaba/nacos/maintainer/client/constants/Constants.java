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

package com.alibaba.nacos.maintainer.client.constants;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * All the constants.
 *
 * @author Nacos
 */
public class Constants {
    
    public static final String NACOS_PERSIST_ENCODE_KEY = "nacosPersistEncodingKey";
    
    public static final String DEFAULT_NACOS_ENCODE = "UTF-8";
    
    public static final String PERSIST_ENCODE = getPersistEncode();
    
    public static String getPersistEncode() {
        String persistEncode = System.getenv(NACOS_PERSIST_ENCODE_KEY);
        if (StringUtils.isBlank(persistEncode)) {
            persistEncode = System.getProperty(NACOS_PERSIST_ENCODE_KEY);
            if (StringUtils.isBlank(persistEncode)) {
                persistEncode = DEFAULT_NACOS_ENCODE;
            }
        }
        return persistEncode;
    }
    
    public static class AdminApiPath {
        
        public static final String CONFIG_ADMIN_PATH = "/v3/admin/cs/config";
        
        public static final String CONFIG_HISTORY_ADMIN_PATH = "/v3/admin/cs/history";
        
        public static final String CONFIG_OPS_ADMIN_PATH = "/v3/admin/cs/ops";
        
        public static final String CONFIG_LISTENER_ADMIN_PATH = "/v3/admin/cs/listener";
        
        public static final String CONFIG_METRICS_ADMIN_PATH = "/v3/admin/cs/metrics";
        
        public static final String NAMING_SERVICE_ADMIN_PATH = "/v3/admin/ns/service";
        
        public static final String NAMING_INSTANCE_ADMIN_PATH = "/v3/admin/ns/instance";
        
        public static final String NAMING_CLUSTER_ADMIN_PATH = "/v3/admin/ns/cluster";
        
        public static final String NAMING_HEALTH_ADMIN_PATH = "/v3/admin/ns/health";
        
        public static final String NAMING_CLIENT_ADMIN_PATH = "/v3/admin/ns/client";
        
        public static final String NAMING_OPS_ADMIN_PATH = "/v3/admin/ns/ops";
        
        public static final String CORE_LOADER_ADMIN_PATH = "/v3/admin/core/loader";
        
        public static final String CORE_CLUSTER_ADMIN_PATH = "/v3/admin/core/cluster";
        
        public static final String CORE_OPS_ADMIN_PATH = "/v3/admin/core/ops";
    }
    
}
