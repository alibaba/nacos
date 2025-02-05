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
    
    public static class SysEnv {
        
        public static final String USER_HOME = "user.home";
        
        public static final String PROJECT_NAME = "project.name";
        
        public static final String JM_LOG_PATH = "JM.LOG.PATH";
        
        public static final String JM_SNAPSHOT_PATH = "JM.SNAPSHOT.PATH";
        
        public static final String NACOS_ENV_FIRST = "nacos.env.first";
        
    }
    
    public static class AdminApiPath {
        
        public static final String CONFIG_ADMIN_PATH = "/v3/admin/cs/config";
        
        public static final String CONFIG_HISTORY_ADMIN_PATH = "/v3/admin/cs/history";
        
        public static final String CONFIG_CAPACITY_ADMIN_PATH = "/v3/admin/cs/capacity";
        
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
    
    public static class Address {
        
        public static final int ENDPOINT_SERVER_LIST_PROVIDER_ORDER = 500;
        
        public static final int ADDRESS_SERVER_LIST_PROVIDER_ORDER = 499;
    }
    
    public static class MemberMetaDataConstants {
        /**
         * Raft port，This parameter is dropped when RPC is used as a whole.
         */
        public static final String RAFT_PORT = "raftPort";
        
        public static final String SITE_KEY = "site";
        
        public static final String AD_WEIGHT = "adWeight";
        
        public static final String WEIGHT = "weight";
        
        public static final String LAST_REFRESH_TIME = "lastRefreshTime";
        
        public static final String VERSION = "version";
        
        public static final String SUPPORT_REMOTE_C_TYPE = "remoteConnectType";
        
        public static final String READY_TO_UPGRADE = "readyToUpgrade";
        
        public static final String SUPPORT_GRAY_MODEL = "supportGrayModel";
        
        public static final String[] BASIC_META_KEYS = new String[] {SITE_KEY, AD_WEIGHT, RAFT_PORT, WEIGHT, VERSION,
                READY_TO_UPGRADE};
    }
    
}
