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

import com.alibaba.nacos.api.config.remote.request.cluster.ConfigChangeClusterSyncRequest;
import com.alibaba.nacos.api.config.remote.response.cluster.ConfigChangeClusterSyncResponse;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.auth.util.AuthHeaderUtil;
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
import com.alibaba.nacos.config.server.remote.ConfigClusterRpcClientProxy;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtil;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Async notify service.
 *
 * @author Nacos
 */
@Service
public class AsyncNotifyService {
    
    @Autowired
    private DumpService dumpService;
    
    @Autowired
    public AsyncNotifyService(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
        
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
                    Queue<NotifySingleTask> httpQueue = new LinkedList<NotifySingleTask>();
                    Queue<NotifySingleRpcTask> rpcQueue = new LinkedList<NotifySingleRpcTask>();
                    
                    for (Member member : ipList) {
                        if (!MemberUtil.isSupportedLongCon(member)) {
                            httpQueue.add(new NotifySingleTask(dataId, group, tenant, tag, dumpTs, member.getAddress(),
                                    evt.isBeta));
                        } else {
                            rpcQueue.add(
                                    new NotifySingleRpcTask(dataId, group, tenant, tag, dumpTs, evt.isBeta, member));
                        }
                    }
                    if (!httpQueue.isEmpty()) {
                        ConfigExecutor.executeAsyncNotify(new AsyncTask(nacosAsyncRestTemplate, httpQueue));
                    }
                    if (!rpcQueue.isEmpty()) {
                        ConfigExecutor.executeAsyncNotify(new AsyncRpcTask(rpcQueue));
                    }
                    
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
    }
    
    private final NacosAsyncRestTemplate nacosAsyncRestTemplate = HttpClientManager.getNacosAsyncRestTemplate();
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncNotifyService.class);
    
    @Autowired
    private ConfigClusterRpcClientProxy configClusterRpcClientProxy;
    
    private ServerMemberManager memberManager;
    
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
                        AuthHeaderUtil.addIdentityToHeader(header);
                        restTemplate.get(task.url, header, Query.EMPTY, String.class, new AsyncNotifyCallBack(task));
                    }
                }
            }
        }
    }
    
    class AsyncRpcTask implements Runnable {
        
        private Queue<NotifySingleRpcTask> queue;
        
        public AsyncRpcTask(Queue<NotifySingleRpcTask> queue) {
            this.queue = queue;
        }
        
        @Override
        public void run() {
            while (!queue.isEmpty()) {
                NotifySingleRpcTask task = queue.poll();
                
                ConfigChangeClusterSyncRequest syncRequest = new ConfigChangeClusterSyncRequest();
                syncRequest.setDataId(task.getDataId());
                syncRequest.setGroup(task.getGroup());
                syncRequest.setBeta(task.isBeta);
                syncRequest.setLastModified(task.getLastModified());
                syncRequest.setTag(task.tag);
                syncRequest.setTenant(task.getTenant());
                Member member = task.member;
                if (memberManager.getSelf().equals(member)) {
                    if (syncRequest.isBeta()) {
                        dumpService.dump(syncRequest.getDataId(), syncRequest.getGroup(), syncRequest.getTenant(),
                                syncRequest.getLastModified(), NetUtils.localIP(), true);
                    } else {
                        dumpService.dump(syncRequest.getDataId(), syncRequest.getGroup(), syncRequest.getTenant(),
                                syncRequest.getTag(), syncRequest.getLastModified(), NetUtils.localIP());
                    }
                    continue;
                }
                
                if (memberManager.hasMember(member.getAddress())) {
                    // start the health check and there are ips that are not monitored, put them directly in the notification queue, otherwise notify
                    boolean unHealthNeedDelay = memberManager.isUnHealth(member.getAddress());
                    if (unHealthNeedDelay) {
                        // target ip is unhealthy, then put it in the notification list
                        ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                                task.getLastModified(), InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_UNHEALTH,
                                0, member.getAddress());
                        // get delay time and set fail count to the task
                        asyncTaskExecute(task);
                    } else {
    
                        if (!MemberUtil.isSupportedLongCon(member)) {
                            asyncTaskExecute(
                                    new NotifySingleTask(task.getDataId(), task.getGroup(), task.getTenant(), task.tag,
                                            task.getLastModified(), member.getAddress(), task.isBeta));
                        } else {
                            try {
                                configClusterRpcClientProxy
                                        .syncConfigChange(member, syncRequest, new AsyncRpcNotifyCallBack(task));
                            } catch (Exception e) {
                                MetricsMonitor.getConfigNotifyException().increment();
                                asyncTaskExecute(task);
                            }
                        }
                      
                    }
                } else {
                    //No nothig if  member has offline.
                }
                
            }
        }
    }
    
    static class NotifySingleRpcTask extends NotifyTask {
        
        private Member member;
        
        private boolean isBeta;
        
        private String tag;
        
        public NotifySingleRpcTask(String dataId, String group, String tenant, String tag, long lastModified,
                boolean isBeta, Member member) {
            super(dataId, group, tenant, lastModified);
            this.member = member;
            this.isBeta = isBeta;
            this.tag = tag;
        }
    }
    
    private void asyncTaskExecute(NotifySingleTask task) {
        int delay = getDelayTime(task);
        Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();
        queue.add(task);
        AsyncTask asyncTask = new AsyncTask(nacosAsyncRestTemplate, queue);
        ConfigExecutor.scheduleAsyncNotify(asyncTask, delay, TimeUnit.MILLISECONDS);
    }
    
    private void asyncTaskExecute(NotifySingleRpcTask task) {
        int delay = getDelayTime(task);
        Queue<NotifySingleRpcTask> queue = new LinkedList<NotifySingleRpcTask>();
        queue.add(task);
        AsyncRpcTask asyncTask = new AsyncRpcTask(queue);
        ConfigExecutor.scheduleAsyncNotify(asyncTask, delay, TimeUnit.MILLISECONDS);
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
    
    class AsyncRpcNotifyCallBack implements RequestCallBack<ConfigChangeClusterSyncResponse> {
        
        private NotifySingleRpcTask task;
        
        public AsyncRpcNotifyCallBack(NotifySingleRpcTask task) {
            this.task = task;
        }
        
        @Override
        public Executor getExecutor() {
            return ConfigExecutor.getConfigSubServiceExecutor();
        }
        
        @Override
        public long getTimeout() {
            return 1000L;
        }
        
        @Override
        public void onResponse(ConfigChangeClusterSyncResponse response) {
            long delayed = System.currentTimeMillis() - task.getLastModified();
            
            if (response.isSuccess()) {
                ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                        task.getLastModified(), InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_OK, delayed,
                        task.member.getAddress());
            } else {
                LOGGER.error("[notify-error] target:{} dataId:{} group:{} ts:{} code:{}", task.member.getAddress(),
                        task.getDataId(), task.getGroup(), task.getLastModified(), response.getErrorCode());
                ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                        task.getLastModified(), InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_ERROR, delayed,
                        task.member.getAddress());
                
                //get delay time and set fail count to the task
                asyncTaskExecute(task);
                
                LogUtil.NOTIFY_LOG.error("[notify-retry] target:{} dataId:{} group:{} ts:{}", task.member.getAddress(),
                        task.getDataId(), task.getGroup(), task.getLastModified());
                
                MetricsMonitor.getConfigNotifyException().increment();
            }
        }
        
        @Override
        public void onException(Throwable ex) {
            long delayed = System.currentTimeMillis() - task.getLastModified();
            LOGGER.error("[notify-exception] target:{} dataId:{} group:{} ts:{} ex:{}", task.member.getAddress(),
                    task.getDataId(), task.getGroup(), task.getLastModified(), ex.toString());
            ConfigTraceService
                    .logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null, task.getLastModified(),
                            InetUtils.getSelfIP(), ConfigTraceService.NOTIFY_EVENT_EXCEPTION, delayed,
                            task.member.getAddress());
            
            //get delay time and set fail count to the task
            asyncTaskExecute(task);
            LogUtil.NOTIFY_LOG.error("[notify-retry] target:{} dataId:{} group:{} ts:{}", task.member.getAddress(),
                    task.getDataId(), task.getGroup(), task.getLastModified());
            
            MetricsMonitor.getConfigNotifyException().increment();
        }
    }
    
    static class NotifySingleTask extends NotifyTask {
        
        private String target;
        
        public String url;
        
        private boolean isBeta;
        
        private static final String URL_PATTERN =
                "http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH + "/dataChange" + "?dataId={2}&group={3}";
        
        private static final String URL_PATTERN_TENANT =
                "http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH + "/dataChange"
                        + "?dataId={2}&group={3}&tenant={4}";
        
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
        
    }
    
    /**
     * get delayTime and also set failCount to task; The failure time index increases, so as not to retry invalid tasks
     * in the offline scene, which affects the normal synchronization.
     *
     * @param task notify task
     * @return delay
     */
    private static int getDelayTime(NotifyTask task) {
        int failCount = task.getFailCount();
        int delay = MIN_RETRY_INTERVAL + failCount * failCount * INCREASE_STEPS;
        if (failCount <= MAX_COUNT) {
            task.setFailCount(failCount + 1);
        }
        return delay;
    }
    
    private static final int MIN_RETRY_INTERVAL = 500;
    
    private static final int INCREASE_STEPS = 1000;
    
    private static final int MAX_COUNT = 6;
    
}
