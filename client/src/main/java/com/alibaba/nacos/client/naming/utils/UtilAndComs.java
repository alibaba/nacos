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

package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.common.utils.VersionUtils;

/**
 * Util and constants.
 *
 * @author xuanyin.zy
 */
public class UtilAndComs {
    
    // using  ClientCommonUtils#VERSION instead.
    @Deprecated
    public static final String VERSION = "Nacos-Java-Client:v" + VersionUtils.version;
    
    public static String webContext = "/nacos";
    
    public static String nacosUrlBase = webContext + "/v1/ns";
    
    public static String nacosUrlInstance = nacosUrlBase + "/instance";
    
    public static String nacosUrlService = nacosUrlBase + "/service";
    
    public static final String ENCODING = "UTF-8";
    
    public static final String ENV_LIST_KEY = "envList";
    
    public static final String ALL_IPS = "000--00-ALL_IPS--00--000";
    
    public static final String FAILOVER_SWITCH = "00-00---000-VIPSRV_FAILOVER_SWITCH-000---00-00";
    
    public static final String DEFAULT_NAMESPACE_ID = "public";
    
    public static final int REQUEST_DOMAIN_RETRY_COUNT = 3;
    
    public static final String NACOS_NAMING_LOG_NAME = "com.alibaba.nacos.naming.log.filename";
    
    public static final String NACOS_NAMING_LOG_LEVEL = "com.alibaba.nacos.naming.log.level";
    
    public static final int DEFAULT_CLIENT_BEAT_THREAD_COUNT =
            ThreadUtils.getSuitableThreadCount(1) > 1 ? ThreadUtils.getSuitableThreadCount(1) / 2 : 1;
    
    public static final int DEFAULT_POLLING_THREAD_COUNT =
            ThreadUtils.getSuitableThreadCount(1) > 1 ? ThreadUtils.getSuitableThreadCount(1) / 2 : 1;
    
    public static final String HTTP = "http://";
    
    public static final String HTTPS = "https://";
    
    public static final String ENV_CONFIGS = "00-00---000-ENV_CONFIGS-000---00-00";
    
    public static final String VIP_CLIENT_FILE = "vipclient.properties";
    
    public static final String ALL_HOSTS = "00-00---000-ALL_HOSTS-000---00-00";
    
}
