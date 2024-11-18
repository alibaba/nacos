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

package com.alibaba.nacos.api.common;

import java.util.concurrent.TimeUnit;

/**
 * Constants.
 *
 * @author Nacos
 */
public class Constants {
    
    public static final String CLIENT_VERSION = "3.0.0";
    
    public static final int DATA_IN_BODY_VERSION = 204;
    
    public static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    
    public static final String APPNAME = "AppName";
    
    public static final String CLIENT_VERSION_KEY = "ClientVersion";
    
    public static final String CLIENT_IP = "ClientIp";
    
    public static final String UNKNOWN_APP = "UnknownApp";
    
    public static final String DEFAULT_DOMAINNAME = "commonconfig.config-host.taobao.com";
    
    public static final String DAILY_DOMAINNAME = "commonconfig.taobao.net";
    
    public static final String NULL = "";
    
    public static final String DATA_ID = "dataId";

    public static final String TENANT = "tenant";

    public static final String GROUP = "group";

    public static final String NAMESPACE_ID = "namespaceId";

    public static final String LAST_MODIFIED = "Last-Modified";
    
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    
    public static final String CONTENT_ENCODING = "Content-Encoding";
    
    public static final String PROBE_MODIFY_REQUEST = "Listening-Configs";
    
    public static final String PROBE_MODIFY_RESPONSE = "Probe-Modify-Response";
    
    public static final String PROBE_MODIFY_RESPONSE_NEW = "Probe-Modify-Response-New";
    
    public static final String USE_ZIP = "true";
    
    public static final String CONTENT_MD5 = "Content-MD5";
    
    public static final String CONFIG_VERSION = "Config-Version";
    
    public static final String CONFIG_TYPE = "Config-Type";
    
    public static final String ENCRYPTED_DATA_KEY = "Encrypted-Data-Key";
    
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    
    public static final String SPACING_INTERVAL = "client-spacing-interval";
    
    public static final String BASE_PATH = "/v1/cs";
    
    public static final String CONFIG_CONTROLLER_PATH = BASE_PATH + "/configs";
    
    public static final String TOKEN = "token";
    
    public static final String ACCESS_TOKEN = "accessToken";
    
    public static final String TOKEN_TTL = "tokenTtl";
    
    public static final String GLOBAL_ADMIN = "globalAdmin";
    
    public static final String USERNAME = "username";
    
    public static final String TOKEN_REFRESH_WINDOW = "tokenRefreshWindow";
    
    public static final Integer SDK_GRPC_PORT_DEFAULT_OFFSET = 1000;
    
    public static final Integer CLUSTER_GRPC_PORT_DEFAULT_OFFSET = 1001;
    
    /**
     * second.
     */
    public static final int ASYNC_UPDATE_ADDRESS_INTERVAL = 300;
    
    /**
     * second.
     */
    public static final int POLLING_INTERVAL_TIME = 15;
    
    /**
     * millisecond.
     */
    public static final int ONCE_TIMEOUT = 2000;
    
    /**
     * millisecond.
     */
    public static final int SO_TIMEOUT = 60000;
    
    /**
     * millisecond.
     */
    public static final int CONFIG_LONG_POLL_TIMEOUT = 30000;
    
    /**
     * millisecond.
     */
    public static final int MIN_CONFIG_LONG_POLL_TIMEOUT = 10000;
    
    /**
     * millisecond.
     */
    public static final int CONFIG_RETRY_TIME = 2000;
    
    /**
     * Maximum number of retries.
     */
    public static final int MAX_RETRY = 3;
    
    /**
     * millisecond.
     */
    public static final int RECV_WAIT_TIMEOUT = ONCE_TIMEOUT * 5;
    
    public static final String ENCODE = "UTF-8";
    
    public static final String MAP_FILE = "map-file.js";
    
    public static final int FLOW_CONTROL_THRESHOLD = 20;
    
    public static final int FLOW_CONTROL_SLOT = 10;
    
    public static final int FLOW_CONTROL_INTERVAL = 1000;
    
    public static final float DEFAULT_PROTECT_THRESHOLD = 0.0F;
    
    public static final String LINE_SEPARATOR = Character.toString((char) 1);
    
    public static final String WORD_SEPARATOR = Character.toString((char) 2);
    
    public static final String LONGPOLLING_LINE_SEPARATOR = "\r\n";
    
    public static final String CLIENT_APPNAME_HEADER = "Client-AppName";
    
    public static final String CLIENT_REQUEST_TS_HEADER = "Client-RequestTS";
    
    public static final String CLIENT_REQUEST_TOKEN_HEADER = "Client-RequestToken";
    
    public static final int ATOMIC_MAX_SIZE = 1000;
    
    public static final String NAMING_INSTANCE_ID_SPLITTER = "#";
    
    public static final int NAMING_INSTANCE_ID_SEG_COUNT = 4;
    
    public static final String NAMING_HTTP_HEADER_SPLITTER = "\\|";
    
    public static final String DEFAULT_CLUSTER_NAME = "DEFAULT";
    
    public static final long DEFAULT_HEART_BEAT_TIMEOUT = TimeUnit.SECONDS.toMillis(15);
    
    public static final long DEFAULT_IP_DELETE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    
    public static final long DEFAULT_HEART_BEAT_INTERVAL = TimeUnit.SECONDS.toMillis(5);
    
    public static final String DEFAULT_NAMESPACE_ID = "public";
    
    public static final boolean DEFAULT_USE_CLOUD_NAMESPACE_PARSING = true;
    
    public static final int WRITE_REDIRECT_CODE = 307;
    
    public static final String SERVICE_INFO_SPLITER = "@@";
    
    public static final int SERVICE_INFO_SPLIT_COUNT = 2;
    
    public static final String NULL_STRING = "null";
    
    public static final String NUMBER_PATTERN_STRING = "^\\d+$";
    
    public static final String ANY_PATTERN = ".*";
    
    public static final String DEFAULT_INSTANCE_ID_GENERATOR = "simple";
    
    public static final String SNOWFLAKE_INSTANCE_ID_GENERATOR = "snowflake";
    
    public static final String HTTP_PREFIX = "http";
    
    public static final String ALL_PATTERN = "*";
    
    public static final String COLON = ":";
    
    public static final String LINE_BREAK = "\n";
    
    public static final String POUND = "#";
    
    public static final String VIPSERVER_TAG = "Vipserver-Tag";
    
    public static final String AMORY_TAG = "Amory-Tag";
    
    public static final String LOCATION_TAG = "Location-Tag";
    
    public static final String CHARSET_KEY = "charset";
    
    public static final String CLUSTER_NAME_PATTERN_STRING = "^[0-9a-zA-Z-]+$";
    
    /**
     * millisecond.
     */
    public static final long DEFAULT_REDO_DELAY_TIME = 3000L;
    
    public static final int DEFAULT_REDO_THREAD_COUNT = 1;
    
    public static final String APP_CONN_LABELS_KEY = "nacos.app.conn.labels";
    
    public static final String DOT = ".";
    
    public static final String WEIGHT = "weight";
    
    public static final String PROPERTIES_KEY = "properties";
    
    public static final String JVM_KEY = "jvm";
    
    public static final String ENV_KEY = "env";
    
    public static final String APP_CONN_LABELS_PREFERRED = "nacos_app_conn_labels_preferred";
    
    public static final String APP_CONN_PREFIX = "app_";
    
    public static final String CONFIG_GRAY_LABEL = "nacos.config.gray.label";
    
    /**
     * Since 2.3.3, For some situation like java agent using nacos-client which can't use env ram info.
     */
    public static final String DEFAULT_USE_RAM_INFO_PARSING = "true";
    
    public static final String CLIENT_MODULE_TYPE = "clientModuleType";
    
    /**
     * The constants in config directory.
     */
    public static class Config {
        
        public static final String CONFIG_MODULE = "config";
        
        public static final String NOTIFY_HEADER = "notify";
    }
    
    /**
     * The constants in naming directory.
     */
    public static class Naming {
        
        public static final String NAMING_MODULE = "naming";
        
        public static final String CMDB_CONTEXT_TYPE = "CMDB";
    }
    
    /**
     * The constants in remote directory.
     */
    public static class Remote {
        
        public static final String INTERNAL_MODULE = "internal";
    }
    
    /**
     * The constants in exception directory.
     */
    public static class Exception {
        
        public static final int SERIALIZE_ERROR_CODE = 100;
        
        public static final int DESERIALIZE_ERROR_CODE = 101;
        
        public static final int FIND_DATASOURCE_ERROR_CODE = 102;
        
        public static final int FIND_TABLE_ERROR_CODE = 103;
    }
}
