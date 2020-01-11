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
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.naming.healthcheck.JsonAdapter;
import com.alibaba.nacos.naming.selector.Selector;
import com.alibaba.nacos.naming.selector.SelectorJsonAdapter;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static com.alibaba.nacos.core.utils.SystemUtils.NACOS_HOME;

/**
 * @author nacos
 * @author jifengnan
 */
public class UtilsAndCommons {

    // ********************** Nacos HTTP Context ************************ \\

    public static final String NACOS_SERVER_CONTEXT = "/nacos";

    public static final String NACOS_SERVER_VERSION = "/v1";

    public static final String DEFAULT_NACOS_NAMING_CONTEXT = NACOS_SERVER_VERSION + "/ns";

    public static final String NACOS_NAMING_CONTEXT = DEFAULT_NACOS_NAMING_CONTEXT;

    public static final String NACOS_NAMING_CATALOG_CONTEXT = "/catalog";

    public static final String NACOS_NAMING_INSTANCE_CONTEXT = "/instance";

    public static final String NACOS_NAMING_SERVICE_CONTEXT = "/service";

    public static final String NACOS_NAMING_CLUSTER_CONTEXT = "/cluster";

    public static final String NACOS_NAMING_HEALTH_CONTEXT = "/health";

    public static final String NACOS_NAMING_RAFT_CONTEXT = "/raft";

    public static final String NACOS_NAMING_PARTITION_CONTEXT = "/distro";

    public static final String NACOS_NAMING_OPERATOR_CONTEXT = "/operator";

    // ********************** Nacos HTTP Context ************************ //

    public static final String NACOS_SERVER_HEADER = "Nacos-Server";

    public static final String NACOS_VERSION = VersionUtils.VERSION;

    public static final String SUPER_TOKEN = "xy";

    public static final String DOMAINS_DATA_ID_PRE = "com.alibaba.nacos.naming.domains.meta.";

    public static final String IPADDRESS_DATA_ID_PRE = "com.alibaba.nacos.naming.iplist.";

    public static final String SWITCH_DOMAIN_NAME = "00-00---000-NACOS_SWITCH_DOMAIN-000---00-00";

    public static final String CIDR_REGEX = "[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}/[0-9]+";

    public static final String UNKNOWN_SITE = "unknown";

    public static final String DEFAULT_CLUSTER_NAME = "DEFAULT";

    public static final String LOCALHOST_SITE = UtilsAndCommons.UNKNOWN_SITE;

    public static final int RAFT_PUBLISH_TIMEOUT = 5000;

    public static final String SERVER_VERSION = NACOS_SERVER_HEADER + ":" + NACOS_VERSION;

    public static final String SELF_SERVICE_CLUSTER_ENV = "naming_self_service_cluster_ips";

    public static final String CACHE_KEY_SPLITER = "@@@@";

    public static final String LOCAL_HOST_IP = "127.0.0.1";

    public static final String IP_PORT_SPLITER = ":";

    public static final int MAX_PUBLISH_WAIT_TIME_MILLIS = 5000;

    public static final String VERSION_STRING_SYNTAX = "[0-9]+\\.[0-9]+\\.[0-9]+";

    public static final String API_UPDATE_SWITCH = "/api/updateSwitch";

    public static final String API_SET_ALL_WEIGHTS = "/api/setWeight4AllIPs";

    public static final String API_DOM = "/api/dom";

    public static final String NAMESPACE_SERVICE_CONNECTOR = "##";

    public static final String UPDATE_INSTANCE_ACTION_ADD = "add";

    public static final String UPDATE_INSTANCE_ACTION_REMOVE = "remove";

    public static final String DATA_BASE_DIR = NACOS_HOME + File.separator + "data" + File.separator + "naming";

    public static final String NUMBER_PATTERN = "^\\d+$";

    public static final ScheduledExecutorService SERVICE_SYNCHRONIZATION_EXECUTOR;

    public static final ScheduledExecutorService SERVICE_UPDATE_EXECUTOR;

    public static final ScheduledExecutorService INIT_CONFIG_EXECUTOR;

    public static final Executor RAFT_PUBLISH_EXECUTOR;

    static {

        // custom serializer and deserializer for fast-json
        SerializeConfig.getGlobalInstance()
            .put(AbstractHealthChecker.class, JsonAdapter.getInstance());
        ParserConfig.getGlobalInstance()
            .putDeserializer(AbstractHealthChecker.class, JsonAdapter.getInstance());

        SerializeConfig.getGlobalInstance()
            .put(Selector.class, SelectorJsonAdapter.getInstance());
        ParserConfig.getGlobalInstance()
            .putDeserializer(Selector.class, SelectorJsonAdapter.getInstance());

        // write null values, otherwise will cause compatibility issues
        JSON.DEFAULT_GENERATE_FEATURE |= SerializerFeature.WriteNullStringAsEmpty.getMask();
        JSON.DEFAULT_GENERATE_FEATURE |= SerializerFeature.WriteNullListAsEmpty.getMask();
        JSON.DEFAULT_GENERATE_FEATURE |= SerializerFeature.WriteNullBooleanAsFalse.getMask();
        JSON.DEFAULT_GENERATE_FEATURE |= SerializerFeature.WriteMapNullValue.getMask();
        JSON.DEFAULT_GENERATE_FEATURE |= SerializerFeature.WriteNullNumberAsZero.getMask();

        SERVICE_SYNCHRONIZATION_EXECUTOR
            = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("nacos.naming.service.worker");
                t.setDaemon(true);
                return t;
            }
        });

        SERVICE_UPDATE_EXECUTOR
            = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("nacos.naming.service.update.processor");
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

        RAFT_PUBLISH_EXECUTOR
            = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("nacos.naming.raft.publisher");
                t.setDaemon(true);
                return t;
            }
        });

    }

    public static String getSwitchDomainKey() {
        return UtilsAndCommons.DOMAINS_DATA_ID_PRE + UtilsAndCommons.SWITCH_DOMAIN_NAME;
    }

    public static Map<String, String> parseMetadata(String metadata) throws NacosException {

        Map<String, String> metadataMap = new HashMap<>(16);

        if (StringUtils.isBlank(metadata)) {
            return metadataMap;
        }

        try {
            metadataMap = JSON.parseObject(metadata, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            String[] datas = metadata.split(",");
            if (datas.length > 0) {
                for (String data : datas) {
                    String[] kv = data.split("=");
                    if (kv.length != 2) {
                        throw new NacosException(NacosException.INVALID_PARAM, "metadata format incorrect:" + metadata);
                    }
                    metadataMap.put(kv[0], kv[1]);
                }
            }
        }

        return metadataMap;
    }

    public static String assembleFullServiceName(String namespaceId, String serviceName) {
        return namespaceId + UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR + serviceName;
    }

    /**
     * Provide a number between 0(inclusive) and {@code upperLimit}(exclusive) for the given {@code string},
     * the number will be nearly uniform distribution.
     * <p>
     * <p>
     *
     * e.g. Assume there's an array which contains some IP of the servers provide the same service,
     * the caller name can be used to choose the server to achieve load balance.
     * <blockquote><pre>
     *     String[] serverIps = new String[10];
     *     int index = shakeUp("callerName", serverIps.length);
     *     String targetServerIp = serverIps[index];
     * </pre></blockquote>
     *
     * @param string     a string. the number 0 will be returned if it's null
     * @param upperLimit the upper limit of the returned number, must be a positive integer, which means > 0
     * @return a number between 0(inclusive) and upperLimit(exclusive)
     * @throws IllegalArgumentException if the upper limit equals or less than 0
     * @since 1.0.0
     * @author jifengnan
     */
    public static int shakeUp(String string, int upperLimit) {
        if (upperLimit < 1) {
            throw new IllegalArgumentException("upper limit must be greater than 0");
        }
        if (string == null) {
            return 0;
        }
        return (string.hashCode() & Integer.MAX_VALUE) % upperLimit;
    }

}
