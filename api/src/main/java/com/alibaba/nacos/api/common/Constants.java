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

/**
 * Constant
 * 
 * @author Nacos
 *
 */
public class Constants {

	public static final String CLIENT_VERSION_HEADER = "Client-Version";

	public static final String CLIENT_VERSION = "3.0.0";

	public static int DATA_IN_BODY_VERSION = 204;

	public static final String DEFAULT_GROUP = "DEFAULT_GROUP";

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

	public static final String BASE_PATH = "/v1/cs";

	public static final String CONFIG_CONTROLLER_PATH = BASE_PATH + "/configs";

	/**
	 * second
	 */
	public static final int ASYNC_UPDATE_ADDRESS_INTERVAL = 300;

	/**
	 * second
	 */
	public static final int POLLING_INTERVAL_TIME = 15;

	/**
	 *  millisecond
	 */
	public static final int ONCE_TIMEOUT = 2000;

	/**
	 *  millisecond
	 */
	public static final int CONN_TIMEOUT = 2000;

	/**
	 *  millisecond
	 */
	public static final int SO_TIMEOUT = 60000;

	/**
	 *  millisecond
	 */
	public static final int RECV_WAIT_TIMEOUT = ONCE_TIMEOUT * 5;

	public static final String ENCODE = "UTF-8";

	public static final String MAP_FILE = "map-file.js";

	public static final int FLOW_CONTROL_THRESHOLD = 20;

	public static final int FLOW_CONTROL_SLOT = 10;

	public static final int FLOW_CONTROL_INTERVAL = 1000;

	public static final String LINE_SEPARATOR = Character.toString((char) 1);

	public static final String WORD_SEPARATOR = Character.toString((char) 2);

	public static final String LONGPULLING_LINE_SEPARATOR = "\r\n";

	public static final String CLIENT_APPNAME_HEADER = "Client-AppName";
	public static final String CLIENT_REQUEST_TS_HEADER = "Client-RequestTS";
	public static final String CLIENT_REQUEST_TOKEN_HEADER = "Client-RequestToken";

	public static final int ATOMIC_MAX_SIZE = 1000;

	public static final String NAMING_INSTANCE_ID_SPLITTER = "#";
	public static final int NAMING_INSTANCE_ID_SEG_COUNT = 4;
}
