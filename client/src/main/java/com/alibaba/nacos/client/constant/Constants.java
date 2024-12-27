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

package com.alibaba.nacos.client.constant;

import java.util.concurrent.TimeUnit;

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
    
    public static class Disk {
    
        public static final String READ_ONLY = "r";
    
        public static final String READ_WRITE = "rw";
    }
    
    public static class HealthCheck {
        
        public static final String UP = "UP";
        
        public static final String DOWN = "DOWN";
    }
    
    public static class Security {
    
        public static final long SECURITY_INFO_REFRESH_INTERVAL_MILLS = TimeUnit.SECONDS.toMillis(5);
        
    }
    
    public static class Address {
        
        public static final int ENDPOINT_SERVER_LIST_PROVIDER_ORDER = 500;
        
        public static final int ADDRESS_SERVER_LIST_PROVIDER_ORDER = 499;
    }
    
}
