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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Component;

import static com.alibaba.nacos.naming.constants.Constants.DATA_WARMUP;
import static com.alibaba.nacos.naming.constants.Constants.EMPTY_SERVICE_CLEAN_INTERVAL;
import static com.alibaba.nacos.naming.constants.Constants.EMPTY_SERVICE_EXPIRED_TIME;
import static com.alibaba.nacos.naming.constants.Constants.EXPIRED_METADATA_CLEAN_INTERVAL;
import static com.alibaba.nacos.naming.constants.Constants.EXPIRED_METADATA_EXPIRED_TIME;
import static com.alibaba.nacos.naming.constants.Constants.EXPIRE_INSTANCE;

/**
 * Stores some configurations for Distro protocol.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class GlobalConfig {
    
    public boolean isDataWarmup() {
        return EnvUtil.getProperty(DATA_WARMUP, Boolean.class, false);
    }
    
    public boolean isExpireInstance() {
        return EnvUtil.getProperty(EXPIRE_INSTANCE, Boolean.class, true);
    }
    
    public static Long getEmptyServiceCleanInterval() {
        return EnvUtil.getProperty(EMPTY_SERVICE_CLEAN_INTERVAL, Long.class, 60000L);
    }
    
    public static Long getEmptyServiceExpiredTime() {
        return EnvUtil.getProperty(EMPTY_SERVICE_EXPIRED_TIME, Long.class, 60000L);
    }
    
    public static Long getExpiredMetadataCleanInterval() {
        return EnvUtil.getProperty(EXPIRED_METADATA_CLEAN_INTERVAL, Long.class, 5000L);
    }
    
    public static Long getExpiredMetadataExpiredTime() {
        return EnvUtil.getProperty(EXPIRED_METADATA_EXPIRED_TIME, Long.class, 60000L);
    }
    
}
