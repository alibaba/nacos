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
 * @author onew
 */
public class Constants {
    
    public static class SysEnv {
        
        public static final String USER_HOME = "user.home";
        
        public static final String PROJECT_NAME = "project.name";
        
        public static final String JM_LOG_PATH = "JM.LOG.PATH";
        
        public static final String JM_SNAPSHOT_PATH = "JM.SNAPSHOT.PATH";
        
        public static final String NACOS_ENV_FIRST = "nacos.env.first";
        
    }
    
    public static class AdminApiPath {
        
        public static final String CONFIG_ADMIN_PATH = "/v3/admin/cs/config";

    }
    
    public static class Address {
        
        public static final int ENDPOINT_SERVER_LIST_PROVIDER_ORDER = 500;
        
        public static final int ADDRESS_SERVER_LIST_PROVIDER_ORDER = 499;
    }
    
    static String getPersistEncode() {
        String persistEncode = System.getenv(NACOS_PERSIST_ENCODE_KEY);
        if (StringUtils.isBlank(persistEncode)) {
            persistEncode = System.getProperty(NACOS_PERSIST_ENCODE_KEY);
            if (StringUtils.isBlank(persistEncode)) {
                persistEncode = DEFAULT_NACOS_ENCODE;
            }
        }
        return persistEncode;
    }
    
    public static final String NACOS_PERSIST_ENCODE_KEY = "nacosPersistEncodingKey";
    
    public static final String DEFAULT_NACOS_ENCODE = "UTF-8";
    
    public static final String PERSIST_ENCODE = getPersistEncode();
    
}
