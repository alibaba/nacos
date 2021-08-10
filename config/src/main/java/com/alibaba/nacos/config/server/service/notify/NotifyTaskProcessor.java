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

package com.alibaba.nacos.config.server.service.notify;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Notification service. After the database changes, notify all servers, including themselves, to load new data.
 *
 * @author Nacos
 */
public class NotifyTaskProcessor implements NacosTaskProcessor {
    
    static final Logger LOGGER = LoggerFactory.getLogger(NotifyTaskProcessor.class);
    
    static final String URL_PATTERN =
            "http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH + "/dataChange" + "?dataId={2}&group={3}";
    
    final ServerMemberManager memberManager;
    
    public NotifyTaskProcessor(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }
    
    @Override
    public boolean process(NacosTask task) {
        NotifyTask notifyTask = (NotifyTask) task;
        String dataId = notifyTask.getDataId();
        String group = notifyTask.getGroup();
        String tenant = notifyTask.getTenant();
        long lastModified = notifyTask.getLastModified();
        
        boolean isok = true;
        
        for (Member ip : memberManager.allMembers()) {
            isok = notifyToDump(dataId, group, tenant, lastModified, ip.getAddress()) && isok;
        }
        return isok;
    }
    
    /**
     * Notify other servers.
     */
    boolean notifyToDump(String dataId, String group, String tenant, long lastModified, String serverIp) {
        long delayed = System.currentTimeMillis() - lastModified;
        try {
            /*
             In order to facilitate the system beta, without changing the notify.do interface,
             the new lastModifed parameter is passed through the Http header
             */
            List<String> headers = Arrays
                    .asList(NotifyService.NOTIFY_HEADER_LAST_MODIFIED, String.valueOf(lastModified),
                            NotifyService.NOTIFY_HEADER_OP_HANDLE_IP, InetUtils.getSelfIP());
            String urlString = MessageFormat
                    .format(URL_PATTERN, serverIp, EnvUtil.getContextPath(), dataId, group);
            
            RestResult<String> result = NotifyService.invokeURL(urlString, headers, Constants.ENCODE);
            if (result.ok()) {
                ConfigTraceService.logNotifyEvent(dataId, group, tenant, null, lastModified, InetUtils.getSelfIP(),
                        ConfigTraceService.NOTIFY_EVENT_OK, delayed, serverIp);
                
                MetricsMonitor.getNotifyRtTimer().record(delayed, TimeUnit.MILLISECONDS);
                
                return true;
            } else {
                MetricsMonitor.getConfigNotifyException().increment();
                LOGGER.error("[notify-error] {}, {}, to {}, result {}", dataId, group, serverIp, result.getCode());
                ConfigTraceService.logNotifyEvent(dataId, group, tenant, null, lastModified, InetUtils.getSelfIP(),
                        ConfigTraceService.NOTIFY_EVENT_ERROR, delayed, serverIp);
                return false;
            }
        } catch (Exception e) {
            MetricsMonitor.getConfigNotifyException().increment();
            String message = "[notify-exception] " + dataId + ", " + group + ", to " + serverIp + ", " + e.toString();
            LOGGER.error(message);
            LOGGER.debug(message, e);
            ConfigTraceService.logNotifyEvent(dataId, group, tenant, null, lastModified, InetUtils.getSelfIP(),
                    ConfigTraceService.NOTIFY_EVENT_EXCEPTION, delayed, serverIp);
            return false;
        }
    }
}
