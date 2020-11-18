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
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Config trace.
 *
 * @author Nacos
 */
@Service
public class ConfigTraceService {
    
    public static final String PERSISTENCE_EVENT_PUB = "pub";
    
    public static final String PERSISTENCE_EVENT_REMOVE = "remove";
    
    public static final String PERSISTENCE_EVENT_MERGE = "merge";
    
    public static final String NOTIFY_EVENT_OK = "ok";
    
    public static final String NOTIFY_EVENT_ERROR = "error";
    
    public static final String NOTIFY_EVENT_UNHEALTH = "unhealth";
    
    public static final String NOTIFY_EVENT_EXCEPTION = "exception";
    
    public static final String DUMP_EVENT_OK = "ok";
    
    public static final String DUMP_EVENT_REMOVE_OK = "remove-ok";
    
    public static final String DUMP_EVENT_ERROR = "error";
    
    public static final String PULL_EVENT_OK = "ok";
    
    public static final String PULL_EVENT_NOTFOUND = "not-found";
    
    public static final String PULL_EVENT_CONFLICT = "conflict";
    
    public static final String PULL_EVENT_ERROR = "error";
    
    /**
     * log persistence event.
     *
     * @param dataId           data id
     * @param group            group
     * @param tenant           tenant
     * @param requestIpAppName request ip app name
     * @param ts               ts
     * @param handleIp         handle ip
     * @param type             type
     * @param content          content
     */
    public static void logPersistenceEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
            String handleIp, String type, String content) {
        if (!LogUtil.TRACE_LOG.isInfoEnabled()) {
            return;
        }
        // Convenient tlog segmentation.
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | handleIp | event | type | [delayed = -1] | ext
        // (md5)
        String md5 = content == null ? null : MD5Utils.md5Hex(content, Constants.ENCODE);
        
        LogUtil.TRACE_LOG.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", InetUtils.getSelfIP(), dataId, group, tenant,
                requestIpAppName, ts, handleIp, "persist", type, -1, md5);
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
            String handleIp, String type, long delayed, String targetIp) {
        if (!LogUtil.TRACE_LOG.isInfoEnabled()) {
            return;
        }
        MetricsMonitor.getNotifyRtTimer().record(delayed, TimeUnit.MILLISECONDS);
        // Convenient tlog segmentation
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | handleIp | event | type | [delayed] | ext
        // (targetIp)
        LogUtil.TRACE_LOG.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", InetUtils.getSelfIP(), dataId, group, tenant,
                requestIpAppName, ts, handleIp, "notify", type, delayed, targetIp);
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
        if (!LogUtil.TRACE_LOG.isInfoEnabled()) {
            return;
        }
        // Convenient tlog segmentation
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | handleIp | event | type | [delayed] | length
        LogUtil.TRACE_LOG.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", InetUtils.getSelfIP(), dataId, group, tenant,
                requestIpAppName, ts, handleIp, "dump", type, delayed, length);
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
        LogUtil.TRACE_LOG
                .info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", InetUtils.getSelfIP(), dataId, group, tenant, requestIpAppName,
                        ts, handleIp, "dump-all", type, -1);
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
     */
    public static void logPullEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
            String type, long delayed, String clientIp, boolean sli) {
        if (!LogUtil.TRACE_LOG.isInfoEnabled()) {
            return;
        }
        // Convenient tlog segmentation
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant| requestIpAppName| ts | event | type | [delayed] | ext(clientIp)
        LogUtil.TRACE_LOG.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", InetUtils.getSelfIP(), dataId, group, tenant,
                requestIpAppName, ts, "pull", type, delayed, clientIp, sli);
    }
    
}
