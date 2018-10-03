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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.nacos.naming.core.Domain;
import com.alibaba.nacos.naming.healthcheck.AbstractHealthCheckConfig;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * @author nacos
 */
public class UtilsAndCommons {

    private static final String NACOS_CONF_DIR_PATH = System.getProperty("user.home") + "/conf";

    private static final String NACOS_CONF_FILE_NAME = "cluster.conf";

    private static String NACOS_CONF_FILE_PATH = NACOS_CONF_DIR_PATH + File.separator + NACOS_CONF_FILE_NAME;

    public static final String NACOS_SERVER_CONTEXT = "/nacos";

    public static final String NACOS_SERVER_VERSION = NACOS_SERVER_CONTEXT + "/v1";

    public static final String DEFAULT_NACOS_NAMING_CONTEXT = NACOS_SERVER_VERSION + "/ns";

    public static final String NACOS_NAMING_CONTEXT = "${nacos.naming.context.path" + ":" + DEFAULT_NACOS_NAMING_CONTEXT + "}";

    public static final String NACOS_NAMING_INSTANCE_CONTEXT = "/instance";

    public static final String NACOS_NAMING_RAFT_CONTEXT = "/raft";

    public static final String NACOS_SERVER_HEADER = "Nacos-Server";

    public static final String NACOS_VERSION = "1.0";

    public static final String SUPER_TOKEN = "xy";

    public static final String DOMAINS_DATA_ID = "com.alibaba.nacos.naming.domains.meta";

    public static final String IPADDRESS_DATA_ID_PRE = "com.alibaba.nacos.naming.iplist.";

    static public final String NODE_TAG_IP_PRE = "com.alibaba.nacos.naming.tag.iplist.";

    public static final String TAG_DOMAINS_DATA_ID = "com.alibaba.nacos.naming.domains.tag.meta";

    static public final String CIDR_REGEX = "[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}/[0-9]+";

    static public final String UNKNOWN_SITE = "unknown";

    static public final String UNKNOWN_HOST = "unknown";

    public static final String DEFAULT_CLUSTER_NAME = "DEFAULT";

    static public final String RAFT_DOM_PRE = "meta";
    static public final String RAFT_IPLIST_PRE = "iplist.";
    static public final String RAFT_TAG_DOM_PRE = "tag.meta";
    static public final String RAFT_TAG_IPLIST_PRE = "tag.iplist.";

    public static final String SERVER_VERSION = NACOS_SERVER_HEADER + ":" + NACOS_VERSION;

    public static final String SELF_SERVICE_CLUSTER_ENV = "naming_self_service_cluster_ips";

    public static final String CACHE_KEY_SPLITER = "@@@@";

    public static final String LOCAL_HOST_IP = "127.0.0.1";

    public static final String CLUSTER_CONF_IP_SPLITER = ":";

    public static final int MAX_PUBLISH_WAIT_TIME_MILLIS = 5000;

    public static final String VERSION_STRING_SYNTAX = "[0-9]+\\.[0-9]+\\.[0-9]+";

    public static final String API_UPDATE_SWITCH = "/api/updateSwitch";

    public static final String API_SET_ALL_WEIGHTS = "/api/setWeight4AllIPs";

    public static final String API_DOM_SERVE_STATUS = "/api/domServeStatus";

    public static final String API_IP_FOR_DOM = "/api/ip4Dom";

    public static final String API_DOM = "/api/dom";

    public static final ScheduledExecutorService SERVER_STATUS_EXECUTOR;

    public static final ScheduledExecutorService DOMAIN_SYNCHRONIZATION_EXECUTOR;

    public static final ScheduledExecutorService DOMAIN_UPDATE_EXECUTOR;

    public static final ScheduledExecutorService INIT_CONFIG_EXECUTOR;

    static {
        // custom serializer and deserializer for fast-json
        SerializeConfig.getGlobalInstance()
                .put(AbstractHealthCheckConfig.class, AbstractHealthCheckConfig.JsonAdapter.getInstance());
        ParserConfig.getGlobalInstance()
                .putDeserializer(AbstractHealthCheckConfig.class, AbstractHealthCheckConfig.JsonAdapter.getInstance());

        // write null values, otherwise will cause compatibility issues
        JSON.DEFAULT_GENERATE_FEATURE |= SerializerFeature.WriteNullStringAsEmpty.getMask();
        JSON.DEFAULT_GENERATE_FEATURE |= SerializerFeature.WriteNullListAsEmpty.getMask();
        JSON.DEFAULT_GENERATE_FEATURE |= SerializerFeature.WriteNullBooleanAsFalse.getMask();
        JSON.DEFAULT_GENERATE_FEATURE |= SerializerFeature.WriteMapNullValue.getMask();
        JSON.DEFAULT_GENERATE_FEATURE |= SerializerFeature.WriteNullNumberAsZero.getMask();

        String nacosHome = System.getProperty("nacos.home");

        if (StringUtils.isNotBlank(nacosHome)) {
            NACOS_CONF_FILE_PATH = nacosHome + File.separator + "conf" + File.separator + NACOS_CONF_FILE_NAME;
        }

        DOMAIN_SYNCHRONIZATION_EXECUTOR
                = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("nacos.naming.domains.worker");
                t.setDaemon(true);
                return t;
            }
        });

        DOMAIN_UPDATE_EXECUTOR
                = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("nacos.naming.domains.update.processor");
                t.setDaemon(true);
                return t;
            }
        });

        INIT_CONFIG_EXECUTOR
                = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("nacos.naming.init.config.worker");
                t.setDaemon(true);
                return t;
            }
        });

        SERVER_STATUS_EXECUTOR
                = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("nacos.naming.status.worker");
                t.setDaemon(true);
                return t;
            }
        });

    }

    public static String getAllExceptionMsg(Throwable e) {
        Throwable cause = e;
        StringBuilder strBuilder = new StringBuilder();

        while (cause != null && !StringUtils.isEmpty(cause.getMessage())) {
            strBuilder.append("caused: ").append(cause.getMessage()).append(";");
            cause = cause.getCause();
        }

        return strBuilder.toString();
    }

    public static String getConfFilePath() {
        return NACOS_CONF_FILE_PATH;
    }

    public static File getConfFile() {
        return new File(getConfFilePath());
    }


    public static String getIPListStoreKey(Domain dom) {
        return UtilsAndCommons.IPADDRESS_DATA_ID_PRE + dom.getName();
    }

    public static String getDomStoreKey(Domain dom) {
        return UtilsAndCommons.DOMAINS_DATA_ID + "." + dom.getName();
    }

}
