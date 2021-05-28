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

package com.alibaba.nacos.config.server.constant;

import com.alibaba.nacos.config.server.model.event.ConfigDumpEvent;

/**
 * Server Constants.
 *
 * @author Nacos
 */
public class Constants {
    
    public static final String CLIENT_VERSION_HEADER = "Client-Version";
    
    public static final String CLIENT_VERSION = "3.0.0";
    
    public static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    
    /**
     * Config file directory in server side.
     *
     */
    public static final String BASE_DIR = "config-data";
    
    /**
     * Back up file directory in server side.
     *
     */
    public static final String CONFIG_BAK_DIR = System.getProperty("user.home", "/home/admin") + "/nacos/bak_data";
    
    public static final String APPNAME = "AppName";
    
    public static final String UNKNOWN_APP = "UnknownApp";
    
    public static final String DEFAULT_DOMAINNAME = "commonconfig.config-host.taobao.com";
    
    public static final String DAILY_DOMAINNAME = "commonconfig.taobao.net";
    
    public static final String NULL = "";
    
    public static final String DATAID = "dataId";
    
    public static final String GROUP = "group";
    
    public static final String LAST_MODIFIED = "Last-Modified";
    
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    
    public static final String CONTENT_ENCODING = "Content-Encoding";
    
    public static final String PROBE_MODIFY_REQUEST = "Listening-Configs";
    
    public static final String PROBE_MODIFY_RESPONSE = "Probe-Modify-Response";
    
    public static final String PROBE_MODIFY_RESPONSE_NEW = "Probe-Modify-Response-New";
    
    public static final String USE_ZIP = "true";
    
    public static final String CONTENT_MD5 = "Content-MD5";
    
    public static final String CONFIG_VERSION = "Config-Version";
    
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    
    public static final String SPACING_INTERVAL = "client-spacing-interval";
    
    /**
     * Interval for async update address(unit: second).
     */
    public static final int ASYNC_UPDATE_ADDRESS_INTERVAL = 300;
    
    /**
     * Interval for polling(unit: second).
     */
    public static final int POLLING_INTERVAL_TIME = 15;
    
    /**
     * Unit: millisecond.
     */
    public static final int ONCE_TIMEOUT = 2000;
    
    /**
     * Unit: millisecond.
     */
    public static final int CONN_TIMEOUT = 2000;
    
    /**
     * Unit: millisecond.
     */
    public static final int SO_TIMEOUT = 60000;
    
    /**
     * Unit: millisecond.
     */
    public static final int RECV_WAIT_TIMEOUT = ONCE_TIMEOUT * 5;
    
    public static final String BASE_PATH = "/v1/cs";
    
    public static final String OPS_CONTROLLER_PATH = BASE_PATH + "/ops";
    
    public static final String CAPACITY_CONTROLLER_PATH = BASE_PATH + "/capacity";
    
    public static final String COMMUNICATION_CONTROLLER_PATH = BASE_PATH + "/communication";
    
    public static final String CONFIG_CONTROLLER_PATH = BASE_PATH + "/configs";
    
    public static final String HEALTH_CONTROLLER_PATH = BASE_PATH + "/health";
    
    public static final String HISTORY_CONTROLLER_PATH = BASE_PATH + "/history";
    
    public static final String LISTENER_CONTROLLER_PATH = BASE_PATH + "/listener";
    
    public static final String NAMESPACE_CONTROLLER_PATH = BASE_PATH + "/namespaces";
    
    public static final String METRICS_CONTROLLER_PATH = BASE_PATH + "/metrics";
    
    public static final String ENCODE = "UTF-8";
    
    public static final String MAP_FILE = "map-file.js";
    
    public static final int FLOW_CONTROL_THRESHOLD = 20;
    
    public static final int FLOW_CONTROL_SLOT = 10;
    
    public static final int FLOW_CONTROL_INTERVAL = 1000;
    
    public static final String LINE_SEPARATOR = Character.toString((char) 1);
    
    public static final String WORD_SEPARATOR = Character.toString((char) 2);
    
    public static final String NACOS_LINE_SEPARATOR = "\r\n";
    
    /**
     * Total time of threshold value when getting data from network(unit: millisecond).
     */
    public static final long TOTALTIME_FROM_SERVER = 10000;
    
    /**
     * Invalid total time of threshold value when getting data from network(unit: millisecond).
     */
    public static final long TOTALTIME_INVALID_THRESHOLD = 60000;
    
    /**
     * When exception or error occurs.
     */
    public static final int BATCH_OP_ERROR = -1;
    
    /**
     * State code of single data when batch operation.
     */
    public static final String BATCH_OP_ERROR_IO_MSG = "get config dump error";
    
    public static final String BATCH_OP_ERROR_CONFLICT_MSG = "config get conflicts";
    
    /**
     * Batch query when data existent.
     */
    public static final int BATCH_QUERY_EXISTS = 1;
    
    public static final String BATCH_QUERY_EXISTS_MSG = "config exits";
    
    /**
     * Batch query when data non-existent.
     */
    public static final int BATCH_QUERY_NONEXISTS = 2;
    
    public static final String BATCH_QUERY_NONEEXISTS_MSG = "config not exits";
    
    /**
     * Batch adding successfully.
     */
    public static final int BATCH_ADD_SUCCESS = 3;
    
    /**
     * Batch updating successfully.
     */
    public static final int BATCH_UPDATE_SUCCESS = 4;
    
    public static final int MAX_UPDATE_FAIL_COUNT = 5;
    
    public static final int MAX_UPDATEALL_FAIL_COUNT = 5;
    
    public static final int MAX_REMOVE_FAIL_COUNT = 5;
    
    public static final int MAX_REMOVEALL_FAIL_COUNT = 5;
    
    public static final int MAX_NOTIFY_COUNT = 5;
    
    public static final int MAX_ADDACK_COUNT = 5;
    
    /**
     * First version of data.
     */
    public static final int FIRST_VERSION = 1;
    
    /**
     * Poison version when data is deleted.
     */
    public static final int POISON_VERSION = -1;
    
    /**
     * Temporary version when disk file is full.
     */
    public static final int TEMP_VERSION = 0;
    
    /**
     * Plain sequence of getting data: backup file -> server -> local file.
     */
    public static final int GETCONFIG_LOCAL_SERVER_SNAPSHOT = 1;
    
    /**
     * Plain sequence of getting data: backup file -> local file -> server.
     */
    public static final int GETCONFIG_LOCAL_SNAPSHOT_SERVER = 2;
    
    public static final String CLIENT_APPNAME_HEADER = "Client-AppName";
    
    public static final String CLIENT_REQUEST_TS_HEADER = "Client-RequestTS";
    
    public static final String CLIENT_REQUEST_TOKEN_HEADER = "Client-RequestToken";
    
    /**
     * Client, identity for sdk request to server.
     */
    public static final String REQUEST_IDENTITY = "Request-Identity";
    
    /**
     * Forward to leader node.
     */
    public static final String FORWARD_LEADER = "Forward-Leader";
    
    /**
     * Acl result information.
     */
    public static final String ACL_RESPONSE = "ACL-Response";
    
    public static final int ATOMIC_MAX_SIZE = 1000;
    
    public static final String CONFIG_MODEL_RAFT_GROUP = "nacos_config";
    
    public static final int DATA_IN_BODY_VERSION = 204;
    
    /**
     * Configure the dump event name.
     */
    public static final String EXTEND_INFO_CONFIG_DUMP_EVENT = ConfigDumpEvent.class.getName();
    
    /**
     * Configure the dump event-list name.
     */
    public static final String EXTEND_INFOS_CONFIG_DUMP_EVENT = ConfigDumpEvent.class.getName() + "@@many";
    
    /**
     * Specifies that reads wait without timeout.
     */
    public static final String EXTEND_NEED_READ_UNTIL_HAVE_DATA = "00--0-read-join-0--00";

    public static final String CONFIG_EXPORT_ITEM_FILE_SEPARATOR = "/";
    
    public static final String CONFIG_EXPORT_METADATA = ".meta.yml";
    
    public static final String CONFIG_EXPORT_METADATA_NEW = ".metadata.yml";
}
