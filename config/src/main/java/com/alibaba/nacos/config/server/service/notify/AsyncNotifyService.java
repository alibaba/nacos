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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Async notify service.
 *
 * @author Nacos
 */
@Service
public class AsyncNotifyService {
    
    public AsyncNotifyService(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
        
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.config.async.delay.notify");
                t.setDaemon(true);
                return t;
            }
        });
        executorService.scheduleWithFixedDelay(delayNotifyProcessor, 2000L, 2000L, TimeUnit.MILLISECONDS);
        
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
                    Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();
                    for (Member member : ipList) {
                        queue.add(new NotifySingleTask(dataId, group, tenant, tag, dumpTs, member.getAddress(),
                                evt.isBeta));
                    }
                    ConfigExecutor.executeAsyncNotify(new AsyncTask(nacosAsyncRestTemplate, queue));
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
    }
    
    private static final NacosAsyncRestTemplate nacosAsyncRestTemplate = HttpClientManager.getNacosAsyncRestTemplate();
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncNotifyService.class);
    
    private ServerMemberManager memberManager;
    
    private DelayNotifyProcessor delayNotifyProcessor = new DelayNotifyProcessor();
    
    
    class AsyncTask implements Runnable {
        
        private Queue<NotifySingleTask> queue;
        
        private NacosAsyncRestTemplate restTemplate;
        
        public AsyncTask(NacosAsyncRestTemplate restTemplate, Queue<NotifySingleTask> queue) {
            this.restTemplate = restTemplate;
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
                        // target ip is unhealthy, then put it in the notification list
                        ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                                task.getLastModified(), InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_UNHEALTH,
                                0, task.target);
                        // get delay time and set fail count to the task
                        asyncTaskExecute(task);
                    } else {
                        Header header = Header.newInstance();
                        header.addParam(NotifyService.NOTIFY_HEADER_LAST_MODIFIED,
                                String.valueOf(task.getLastModified()));
                        header.addParam(NotifyService.NOTIFY_HEADER_OP_HANDLE_IP, InetUtils.getSelfIP());
                        if (task.isBeta) {
                            header.addParam("isBeta", "true");
                        }
                        restTemplate.get(task.url, header, Query.EMPTY, String.class, new AsyncNotifyCallBack(task));
                    }
                }
            }
        }
    }
    
    private void asyncTaskExecute(NotifySingleTask task) {
        task.incDelayTime();
        delayNotifyProcessor.putTaskIfAbsent(task);
    }
    
    class AsyncNotifyCallBack implements Callback<String> {
        
        private NotifySingleTask task;
        
        public AsyncNotifyCallBack(NotifySingleTask task) {
            this.task = task;
        }
        
        @Override
        public void onReceive(RestResult<String> result) {
            
            long delayed = System.currentTimeMillis() - task.getLastModified();
            
            if (result.ok()) {
                ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                        task.getLastModified(), InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_OK, delayed,
                        task.target);
                delayNotifyProcessor.removeTask(task);
            } else {
                LOGGER.error("[notify-error] target:{} dataId:{} group:{} ts:{} code:{}", task.target, task.getDataId(),
                        task.getGroup(), task.getLastModified(), result.getCode());
                ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                        task.getLastModified(), InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_ERROR, delayed,
                        task.target);
                
                //get delay time and set fail count to the task
                asyncTaskExecute(task);
                
                LogUtil.NOTIFY_LOG
                        .error("[notify-retry] target:{} dataId:{} group:{} ts:{}", task.target, task.getDataId(),
                                task.getGroup(), task.getLastModified());
                
                MetricsMonitor.getConfigNotifyException().increment();
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
            
            //get delay time and set fail count to the task
            asyncTaskExecute(task);
            LogUtil.NOTIFY_LOG.error("[notify-retry] target:{} dataId:{} group:{} ts:{}", task.target, task.getDataId(),
                    task.getGroup(), task.getLastModified());
            
            MetricsMonitor.getConfigNotifyException().increment();
        }
        
        @Override
        public void onCancel() {
            
            LogUtil.NOTIFY_LOG.error("[notify-exception] target:{} dataId:{} group:{} ts:{} method:{}", task.target,
                    task.getDataId(), task.getGroup(), task.getLastModified(), "CANCELED");
            
            //get delay time and set fail count to the task
            asyncTaskExecute(task);
            LogUtil.NOTIFY_LOG.error("[notify-retry] target:{} dataId:{} group:{} ts:{}", task.target, task.getDataId(),
                    task.getGroup(), task.getLastModified());
            
            MetricsMonitor.getConfigNotifyException().increment();
        }
    }
    
    static class NotifySingleTask extends NotifyTask {
        
        private static final int MIN_RETRY_INTERVAL = 500;
        
        private static final int INCREASE_STEPS = 1000;
        
        private static final int MAX_COUNT = 6;
        
        private static final String _KEY_SPLITER = "_";
        
        private static final String URL_PATTERN =
                "http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH + "/dataChange" + "?dataId={2}&group={3}";
        
        private static final String URL_PATTERN_TENANT =
                "http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH + "/dataChange"
                        + "?dataId={2}&group={3}&tenant={4}";
        
        private String target;
        
        public String url;
        
        private boolean isBeta;
        
        private int delayTime;
        
        private int failCount;
        
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
            failCount = 0;
            // this.executor = executor;
        }
        
        @Override
        public void setFailCount(int count) {
            this.failCount = count;
        }
        
        @Override
        public int getFailCount() {
            return failCount;
        }
        
        public String getTargetIP() {
            return target;
        }
        
        public int getDelayTime() {
            return delayTime;
        }
        
        /**
         * increase delayTime and also set failCount to task; The failure time index increases, so as not to retry
         * invalid tasks in the offline scene, which affects the normal synchronization.
         */
        private void incDelayTime() {
            this.delayTime = MIN_RETRY_INTERVAL + failCount * failCount * INCREASE_STEPS;
            if (failCount <= MAX_COUNT) {
                failCount++;
            }
        }
        
        private String getSingleTaskKey() {
            return getTargetIP() + _KEY_SPLITER + getDataId() + _KEY_SPLITER + getGroup() + _KEY_SPLITER + getTenant()
                    + _KEY_SPLITER + isBeta;
        }
    }
    
    class DelayNotifyProcessor implements Runnable {
        
        private Map<String, NotifySingleTask> delayNotifyTaskMap = new ConcurrentHashMap<>();
        
        private void putTaskIfAbsent(NotifySingleTask task) {
            delayNotifyTaskMap.putIfAbsent(task.getSingleTaskKey(), task);
        }
        
        private void removeTask(NotifySingleTask task) {
            delayNotifyTaskMap.remove(task.getSingleTaskKey());
        }
        
        @Override
        public void run() {
            for (NotifySingleTask task : delayNotifyTaskMap.values()) {
                Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();
                queue.add(task);
                AsyncTask asyncTask = new AsyncTask(nacosAsyncRestTemplate, queue);
                ConfigExecutor.scheduleAsyncNotify(asyncTask, task.getDelayTime(), TimeUnit.MILLISECONDS);
            }
        }
    }
}
