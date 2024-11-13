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

package com.alibaba.nacos.config.server.service.trace;

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Config trace.
 *
 * @author Nacos
 */
@Service
public class ConfigTraceService {
    
    /**
     * persist event.
     */
    public static final String PERSISTENCE_EVENT = "persist";
    
    public static final String PERSISTENCE_EVENT_BETA = "persist-beta";
    
    public static final String PERSISTENCE_EVENT_TAG = "persist-tag";
    
    /**
     * persist type.
     */
    public static final String PERSISTENCE_TYPE_PUB = "pub";
    
    public static final String PERSISTENCE_TYPE_REMOVE = "remove";
    
    public static final String PERSISTENCE_TYPE_MERGE = "merge";
    
    /**
     * notify event.
     */
    public static final String NOTIFY_EVENT = "notify";
    
    public static final String NOTIFY_EVENT_BETA = "notify-beta";
    
    public static final String NOTIFY_EVENT_BATCH = "notify-batch";
    
    public static final String NOTIFY_EVENT_TAG = "notify-tag";
    
    /**
     * notify type.
     */
    public static final String NOTIFY_TYPE_OK = "ok";
    
    public static final String NOTIFY_TYPE_ERROR = "error";
    
    public static final String NOTIFY_TYPE_UNHEALTH = "unhealth";
    
    public static final String NOTIFY_TYPE_EXCEPTION = "exception";
    
    /**
     * dump event.
     */
    public static final String DUMP_EVENT = "dump";
    
    public static final String DUMP_EVENT_BETA = "dump-beta";
    
    public static final String DUMP_EVENT_BATCH = "dump-batch";
    
    public static final String DUMP_EVENT_TAG = "dump-tag";
    
    /**
     * dump type.
     */
    public static final String DUMP_TYPE_OK = "ok";
    
    public static final String DUMP_TYPE_REMOVE_OK = "remove-ok";
    
    public static final String DUMP_TYPE_ERROR = "error";
    
    /**
     * pull event.
     */
    public static final String PULL_EVENT = "pull";
    
    /**
     * pull type.
     */
    public static final String PULL_TYPE_OK = "ok";
    
    public static final String PULL_TYPE_NOTFOUND = "not-found";
    
    public static final String PULL_TYPE_CONFLICT = "conflict";
    
    public static final String PULL_TYPE_ERROR = "error";
    
    /**
     * log persistence event.
     *
     * @param dataId           data id
     * @param group            group
     * @param tenant           tenant
     * @param requestIpAppName request ip app name
     * @param ts               ts
     * @param handleIp         remote ip
     * @param type             type
     * @param content          content
     */
    public static void logPersistenceEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
            String handleIp, String event, String type, String content) {
        if (!LogUtil.TRACE_LOG.isInfoEnabled()) {
            return;
        }
        // Convenient tlog segmentation.
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | client ip | event | type | [delayed = -1] | ext
        // (md5)
        String md5 = content == null ? null : MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE);
        LogUtil.TRACE_LOG.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", InetUtils.getSelfIP(), dataId, group, tenant,
                requestIpAppName, ts, handleIp, event, type, -1, md5);
    }
    
    /**
     * log notify event.
     *
     * @param dataId           data id
     * @param group            group
     * @param tenant           tenant
     * @param requestIpAppName request ip app name
     * @param ts               ts
     * @param handleIp         handle ip
     * @param type             type
     * @param delayed          delayed
     * @param targetIp         target ip
     */
    public static void logNotifyEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
            String handleIp, String event, String type, long delayed, String targetIp) {
        if (!LogUtil.TRACE_LOG.isInfoEnabled()) {
            return;
        }
        if (delayed < 0) {
            delayed = 0;
        }
        MetricsMonitor.getNotifyRtTimer().record(delayed, TimeUnit.MILLISECONDS);
        // Convenient tlog segmentation
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | handleIp | event | type | [delayed] | ext
        // (targetIp)
        LogUtil.TRACE_LOG.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", InetUtils.getSelfIP(), dataId, group, tenant,
                requestIpAppName, ts, handleIp, event, type, delayed, targetIp);
    }
    
    /**
     * log dump event.
     *
     * @param dataId           data id
     * @param group            group
     * @param tenant           tenant
     * @param requestIpAppName request ip app name
     * @param ts               ts
     * @param handleIp         handle ip
     * @param type             type
     * @param delayed          delayed
     * @param length           length
     */
    public static void logDumpEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
            String handleIp, String type, long delayed, long length) {
        logDumpEventInner(dataId, group, tenant, requestIpAppName, ts, handleIp, ConfigTraceService.DUMP_EVENT, type,
                delayed, length);
    }
    
    public static void logDumpGrayNameEvent(String dataId, String group, String tenant, String grayName,
            String requestIpAppName, long ts, String handleIp, String type, long delayed, long length) {
        logDumpEventInner(dataId, group, tenant, requestIpAppName, ts, handleIp,
                ConfigTraceService.DUMP_EVENT + "-" + grayName, type, delayed, length);
    }
    
    private static void logDumpEventInner(String dataId, String group, String tenant, String requestIpAppName, long ts,
            String handleIp, String event, String type, long delayed, long length) {
        if (!LogUtil.TRACE_LOG.isInfoEnabled()) {
            return;
        }
        if (delayed < 0) {
            delayed = 0;
        }
        MetricsMonitor.getDumpRtTimer().record(delayed, TimeUnit.MILLISECONDS);
        // Convenient tlog segmentation
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | handleIp | event | type | [delayed] | length
        LogUtil.TRACE_LOG.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", InetUtils.getSelfIP(), dataId, group, tenant,
                requestIpAppName, ts, handleIp, event, type, delayed, length);
    }
    
    /**
     * log dump all event.
     *
     * @param dataId           data id
     * @param group            group
     * @param tenant           tenant
     * @param requestIpAppName request ip app name
     * @param ts               ts
     * @param handleIp         handle ip
     * @param type             type
     */
    public static void logDumpAllEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
            String handleIp, String type) {
        if (!LogUtil.TRACE_LOG.isInfoEnabled()) {
            return;
        }
        // Convenient tlog segmentation
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | handleIp | event | type | [delayed = -1]
        LogUtil.TRACE_LOG.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", InetUtils.getSelfIP(), dataId, group, tenant,
                requestIpAppName, ts, handleIp, "dump-all", type, -1);
    }
    
    /**
     * log pull event.
     *
     * @param dataId           data id
     * @param group            group
     * @param tenant           tenant
     * @param requestIpAppName request ip app name
     * @param ts               ts
     * @param type             type
     * @param delayed          delayed
     * @param clientIp         clientIp
     * @param isNotify         isNotify
     * @param model            model
     */
    public static void logPullEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
            String event, String type, long delayed, String clientIp, boolean isNotify, String model) {
        if (!LogUtil.TRACE_LOG.isInfoEnabled()) {
            return;
        }
        // Convenient tlog segmentation
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        if (isNotify && delayed < 0) {
            delayed = 0;
        }
        // localIp | dataid | group | tenant| requestIpAppName| ts | event | type | [delayed] |clientIp| isNotify | mode（http/grpc)
        LogUtil.TRACE_LOG.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", InetUtils.getSelfIP(), dataId, group, tenant,
                requestIpAppName, ts, event, type, delayed, clientIp, isNotify, model);
    }
}
