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
package com.alibaba.nacos.api;

/**
 * Property Key Const
 *
 * @author Nacos
 */
public class PropertyKeyConst {

    public final static String IS_USE_CLOUD_NAMESPACE_PARSING = "isUseCloudNamespaceParsing";

    public final static String IS_USE_ENDPOINT_PARSING_RULE = "isUseEndpointParsingRule";

    public final static String ENDPOINT = "endpoint";

    public final static String ENDPOINT_PORT = "endpointPort";

    public final static String NAMESPACE = "namespace";

    public final static String ACCESS_KEY = "accessKey";

    public final static String SECRET_KEY = "secretKey";

    public final static String RAM_ROLE_NAME = "ramRoleName";

    public final static String SERVER_ADDR = "serverAddr";

    public final static String CONTEXT_PATH = "contextPath";

    public final static String CLUSTER_NAME = "clusterName";

    public final static String ENCODE = "encode";

    public final static String CONFIG_LONG_POLL_TIMEOUT = "configLongPollTimeout";

    public final static String CONFIG_RETRY_TIME = "configRetryTime";

    public final static String MAX_RETRY = "maxRetry";

    public final static String ENABLE_REMOTE_SYNC_CONFIG = "enableRemoteSyncConfig";

    public final static String NAMING_LOAD_CACHE_AT_START = "namingLoadCacheAtStart";

    public final static String NAMING_CLIENT_BEAT_THREAD_COUNT = "namingClientBeatThreadCount";

    public final static String NAMING_POLLING_THREAD_COUNT = "namingPollingThreadCount";

    /**
     * Get the key value of some variable value from the system property
     */
    public static class SystemEnv {

        public static final String ALIBABA_ALIWARE_ENDPOINT_PORT = "ALIBABA_ALIWARE_ENDPOINT_PORT";

        public static final String ALIBABA_ALIWARE_NAMESPACE = "ALIBABA_ALIWARE_NAMESPACE";

        public static final String ALIBABA_ALIWARE_ENDPOINT_URL = "ALIBABA_ALIWARE_ENDPOINT_URL";
    }

}
