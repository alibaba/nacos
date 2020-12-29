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

import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.config.server.utils.LogUtil.MONITOR_LOG;

/**
 * Async notify service.
 *
 * @author Nacos
 */
@Service
public class AsyncNotifyService {
    
    private static final NacosAsyncRestTemplate NACOS_ASYNC_REST_TEMPLATE = HttpClientManager
            .getNacosAsyncRestTemplate();
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncNotifyService.class);
    
    private ServerMemberManager memberManager;
    
    private AsyncDelayNotifyProcessor asyncDelayNotifyProcessor = new AsyncDelayNotifyProcessor();
    
    public AsyncNotifyService(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
        
        ConfigExecutor.scheduleAsyncDelayNotify(asyncDelayNotifyProcessor, 0L, 2000L, TimeUnit.MILLISECONDS);
        
        ConfigExecutor.scheduleAsyncDelayNotify(asyncDelayNotifyProcessor.new StatTask(), 0L, 10L, TimeUnit.SECONDS);
        
        // Register ConfigDataChangeEvent to NotifyCenter.
        NotifyCenter.registerToPublisher(ConfigDataChangeEvent.class, NotifyCenter.ringBufferSize);
        
        // Register A Subscriber to subscribe ConfigDataChangeEvent.
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                // Generate ConfigDataChangeEvent concurrently
                if (event instanceof ConfigDataChangeEvent) {
                    ConfigDataChangeEvent evt = (ConfigDataChangeEvent) event;
                    long dumpTs = evt.lastModifiedTs;
                    String dataId = evt.dataId;
                    String group = evt.group;
                    String tenant = evt.tenant;
                    String tag = evt.tag;
                    Collection<Member> ipList = memberManager.allMembers();
                    
                    // In fact, any type of queue here can be
                    Queue<NotifySingleTask> queue = new LinkedList<>();
                    for (Member member : ipList) {
                        queue.add(new NotifySingleTask(dataId, group, tenant, tag, dumpTs, member.getAddress(),
                                evt.isBeta));
                    }
                    ConfigExecutor.executeAsyncNotify(new AsyncTask(queue));
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
    }
    
    private void putTaskToDelayProcessor(NotifySingleTask task) {
        asyncDelayNotifyProcessor.addTask(task);
    }
    
    private void removeTaskFromDelayProcessor(NotifySingleTask task) {
        asyncDelayNotifyProcessor.removeTask(task);
    }
    
    class AsyncTask implements Runnable {
        
        private Queue<NotifySingleTask> queue;
        
        public AsyncTask(Queue<NotifySingleTask> queue) {
            this.queue = queue;
        }
        
        @Override
        public void run() {
            executeAsyncInvoke();
        }
        
        private void executeAsyncInvoke() {
            while (!queue.isEmpty()) {
                NotifySingleTask task = queue.poll();
                String targetIp = task.getTargetIP();
                if (memberManager.hasMember(targetIp)) {
                    // start the health check and there are ips that are not monitored, put them directly in the notification queue, otherwise notify
                    boolean unHealthNeedDelay = memberManager.isUnHealth(targetIp);
                    if (unHealthNeedDelay) {
                        // target ip is unhealthy, then put it to delay notify processor.
                        ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                                task.getLastModified(), InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_UNHEALTH,
                                0, task.target);
                        putTaskToDelayProcessor(task);
                    } else {
                        AsyncNotifyCallBack callback = new AsyncNotifyCallBack(task);
                        task.executeHttpRequest(callback);
                    }
                }
            }
        }
    }
    
    class NotifySingleTask extends NotifyTask {
        
        private static final int INCREASE_STEPS = 1000;
        
        private static final int MAX_FAIL_COUNT_FOR_INTERVAL = 6;
        
        private static final String KEY_SPLITER = "_";
        
        private static final String URL_PATTERN =
                "http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH + "/dataChange" + "?dataId={2}&group={3}";
        
        private static final String URL_PATTERN_TENANT =
                "http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH + "/dataChange"
                        + "?dataId={2}&group={3}&tenant={4}";
        
        private long startTimestamp;
        
        private String target;
        
        public String url;
        
        private Header header;
        
        private boolean isBeta;
        
        private int failCount;
        
        private boolean isRetry;
        
        private String key;
        
        public NotifySingleTask(String dataId, String group, String tenant, long lastModified, String target) {
            this(dataId, group, tenant, lastModified, target, false);
        }
        
        public NotifySingleTask(String dataId, String group, String tenant, long lastModified, String target,
                boolean isBeta) {
            this(dataId, group, tenant, null, lastModified, target, isBeta);
        }
        
        public NotifySingleTask(String dataId, String group, String tenant, String tag, long lastModified,
                String target, boolean isBeta) {
            super(dataId, group, tenant, lastModified);
            this.target = target;
            this.isBeta = isBeta;
            try {
                dataId = URLEncoder.encode(dataId, Constants.ENCODE);
                group = URLEncoder.encode(group, Constants.ENCODE);
            } catch (UnsupportedEncodingException e) {
                LOGGER.error("URLEncoder encode error", e);
            }
            if (StringUtils.isBlank(tenant)) {
                this.url = MessageFormat.format(URL_PATTERN, target, EnvUtil.getContextPath(), dataId, group);
            } else {
                this.url = MessageFormat
                        .format(URL_PATTERN_TENANT, target, EnvUtil.getContextPath(), dataId, group, tenant);
            }
            if (StringUtils.isNotEmpty(tag)) {
                url = url + "&tag=" + tag;
            }
            
            header = Header.newInstance();
            header.addParam(NotifyService.NOTIFY_HEADER_LAST_MODIFIED, String.valueOf(getLastModified()));
            header.addParam(NotifyService.NOTIFY_HEADER_OP_HANDLE_IP, InetUtils.getSelfIP());
            if (isBeta) {
                header.addParam("isBeta", "true");
            }
            
            failCount = 0;
        }
        
        public String getTargetIP() {
            return target;
        }
        
        public boolean isRetry() {
            return isRetry;
        }
        
        public void setRetry(boolean retry) {
            isRetry = retry;
        }
        
        /**
         * increase delayTime and also set failCount to task; The failure time index increases, so as not to retry
         * invalid tasks in the offline scene, which affects the normal synchronization.
         */
        private void incFailCount() {
            if (failCount == Integer.MAX_VALUE) {
                return;
            }
            failCount++;
        }
        
        private String getSingleTaskKey() {
            if (key == null) {
                key = getTargetIP() + KEY_SPLITER + getDataId() + KEY_SPLITER + getGroup() + KEY_SPLITER + getTenant()
                        + KEY_SPLITER + isBeta;
            }
            return key;
        }
        
        public long getStartTimestamp() {
            return startTimestamp;
        }
        
        public void initStartTimestamp() {
            this.startTimestamp = System.currentTimeMillis();
        }
        
        public void resetStartTimestamp() {
            this.startTimestamp =
                    System.currentTimeMillis() + (failCount <= MAX_FAIL_COUNT_FOR_INTERVAL ? failCount * failCount
                            * INCREASE_STEPS
                            : MAX_FAIL_COUNT_FOR_INTERVAL * MAX_FAIL_COUNT_FOR_INTERVAL * INCREASE_STEPS);
        }
        
        public void executeHttpRequest(Callback<String> callback) {
            NACOS_ASYNC_REST_TEMPLATE.get(url, header, Query.EMPTY, String.class, callback);
        }
    }
    
    class AsyncNotifyCallBack implements Callback<String> {
        
        private static final int MAX_HTTP_RETRY_COUNT = 3;
        
        private NotifySingleTask task;
        
        private int httpFailCount;
        
        public AsyncNotifyCallBack(NotifySingleTask task) {
            this.task = task;
        }
        
        /**
         * <pre>
         * if the task is already retry task, just put it to processor again.
         * if the task is first time notify task, should retry 2 times to request, if all failed, put it to processor.
         * </pre>
         */
        private void callbackError() {
            if (task.isRetry()) {
                putTaskToDelayProcessor(task);
                return;
            }
            httpFailCount++;
            if (httpFailCount == MAX_HTTP_RETRY_COUNT) {
                putTaskToDelayProcessor(task);
                return;
            }
            task.executeHttpRequest(this);
        }
        
        @Override
        public void onReceive(RestResult<String> result) {
            
            long delayed = System.currentTimeMillis() - task.getLastModified();
            
            if (result.ok()) {
                ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                        task.getLastModified(), InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_OK, delayed,
                        task.target);
                removeTaskFromDelayProcessor(task);
            } else {
                LOGGER.error("[notify-error] target:{} dataId:{} group:{} ts:{} code:{}", task.target, task.getDataId(),
                        task.getGroup(), task.getLastModified(), result.getCode());
                ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                        task.getLastModified(), InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_ERROR, delayed,
                        task.target);
                
                LogUtil.NOTIFY_LOG
                        .error("[notify-retry] target:{} dataId:{} group:{} ts:{}", task.target, task.getDataId(),
                                task.getGroup(), task.getLastModified());
                MetricsMonitor.getConfigNotifyException().increment();
                
                callbackError();
            }
        }
        
        @Override
        public void onError(Throwable ex) {
            
            long delayed = System.currentTimeMillis() - task.getLastModified();
            LOGGER.error("[notify-exception] target:{} dataId:{} group:{} ts:{} ex:{}", task.target, task.getDataId(),
                    task.getGroup(), task.getLastModified(), ex.toString());
            ConfigTraceService
                    .logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null, task.getLastModified(),
                            InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_EXCEPTION, delayed, task.target);
            
            LogUtil.NOTIFY_LOG.error("[notify-retry] target:{} dataId:{} group:{} ts:{}", task.target, task.getDataId(),
                    task.getGroup(), task.getLastModified());
            
            MetricsMonitor.getConfigNotifyException().increment();
            
            callbackError();
        }
        
        @Override
        public void onCancel() {
            
            LogUtil.NOTIFY_LOG.error("[notify-exception] target:{} dataId:{} group:{} ts:{} method:{}", task.target,
                    task.getDataId(), task.getGroup(), task.getLastModified(), "CANCELED");
            
            LogUtil.NOTIFY_LOG.error("[notify-retry] target:{} dataId:{} group:{} ts:{}", task.target, task.getDataId(),
                    task.getGroup(), task.getLastModified());
            
            MetricsMonitor.getConfigNotifyException().increment();
            
            callbackError();
        }
    }
    
    class AsyncDelayNotifyProcessor implements Runnable {
        
        private Map<String, NotifySingleTask> delayNotifyTaskMap = new ConcurrentHashMap<>();
        
        private void addTask(NotifySingleTask task) {
            //sign the task is retry task. When it execute, just request once http invoke.
            if (!task.isRetry()) {
                task.setRetry(true);
                task.initStartTimestamp();
            }
            
            NotifySingleTask existTask = delayNotifyTaskMap.putIfAbsent(task.getSingleTaskKey(), task);
            //if the task exist, should extend it's start timestamp.
            if (existTask != null) {
                existTask.incFailCount();
                existTask.resetStartTimestamp();
            }
        }
        
        private void removeTask(NotifySingleTask task) {
            delayNotifyTaskMap.remove(task.getSingleTaskKey());
        }
        
        @Override
        public void run() {
            long current = System.currentTimeMillis();
            for (NotifySingleTask task : delayNotifyTaskMap.values()) {
                //If the target server already be removed in serverList, just remove the task.
                if (!memberManager.hasMember(task.getTargetIP())) {
                    removeTask(task);
                    continue;
                }
                if (current < task.getStartTimestamp()) {
                    continue;
                }
                Queue<NotifySingleTask> queue = new LinkedList<>();
                queue.add(task);
                AsyncTask asyncTask = new AsyncTask(queue);
                ConfigExecutor.executeAsyncNotify(asyncTask);
            }
        }
        
        class StatTask implements Runnable {
            
            @Override
            public void run() {
                MONITOR_LOG.info("[async-delay-notify-task] task count " + delayNotifyTaskMap.size());
                MetricsMonitor.getDelayNotifyTaskMonitor().set(delayNotifyTaskMap.size());
            }
        }
        
    }
    
}
