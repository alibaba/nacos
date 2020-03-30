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

import com.alibaba.nacos.common.utils.Md5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.core.utils.SystemUtils.LOCAL_IP;

/**
 * Config trace
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

    public static void logPersistenceEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
                                           String handleIp, String type, String content) {
        if (!LogUtil.traceLog.isInfoEnabled()) {
            return;
        }
        // 方便tlog切分
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | handleIp | event | type | [delayed = -1] | ext
        // (md5)
        String md5 = content == null ? null : Md5Utils.getMD5(content, Constants.ENCODE);

        LogUtil.traceLog.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", LOCAL_IP, dataId, group, tenant,
            requestIpAppName, ts, handleIp, "persist", type, -1, md5);
    }

    public static void logNotifyEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
                                      String handleIp, String type, long delayed, String targetIp) {
        if (!LogUtil.traceLog.isInfoEnabled()) {
            return;
        }
        MetricsMonitor.getNotifyRtTimer().record(delayed, TimeUnit.MILLISECONDS);
        // 方便tlog切分
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | handleIp | event | type | [delayed] | ext
        // (targetIp)
        LogUtil.traceLog.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", LOCAL_IP, dataId, group, tenant,
            requestIpAppName, ts, handleIp, "notify", type, delayed, targetIp);
    }

    public static void logDumpEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
                                    String handleIp, String type, long delayed, long length) {
        if (!LogUtil.traceLog.isInfoEnabled()) {
            return;
        }
        // 方便tlog切分
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | handleIp | event | type | [delayed] | length
        LogUtil.traceLog.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", LOCAL_IP, dataId, group, tenant,
            requestIpAppName, ts, handleIp, "dump", type, delayed, length);
    }

    public static void logDumpAllEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
                                       String handleIp, String type) {
        if (!LogUtil.traceLog.isInfoEnabled()) {
            return;
        }
        // 方便tlog切分
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant | requestIpAppName | ts | handleIp | event | type | [delayed = -1]
        LogUtil.traceLog.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", LOCAL_IP, dataId, group, tenant,
            requestIpAppName, ts, handleIp, "dump-all", type, -1);
    }

    public static void logPullEvent(String dataId, String group, String tenant, String requestIpAppName, long ts,
                                    String type, long delayed, String clientIp) {
        if (!LogUtil.traceLog.isInfoEnabled()) {
            return;
        }
        // 方便tlog切分
        if (StringUtils.isBlank(tenant)) {
            tenant = null;
        }
        //localIp | dataid | group | tenant| requestIpAppName| ts | event | type | [delayed] | ext(clientIp)
        LogUtil.traceLog.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", LOCAL_IP, dataId, group, tenant,
            requestIpAppName, ts, "pull", type, delayed, clientIp);
    }
}
