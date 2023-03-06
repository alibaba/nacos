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
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.remote.ConfigClusterRpcClientProxy;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncNotifyService.class);
    
    private static final int MIN_RETRY_INTERVAL = 500;
    
    private static final int INCREASE_STEPS = 1000;
    
    private static final int MAX_COUNT = 6;
    
    @Autowired
    private DumpService dumpService;
    
    @Autowired
    private ConfigClusterRpcClientProxy configClusterRpcClientProxy;
    
    private ServerMemberManager memberManager;
    
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
                    
                    MetricsMonitor.incrementConfigChangeCount(tenant, group, dataId);
                    
                    Collection<Member> ipList = memberManager.allMembers();
                    
                    // In fact, any type of queue here can be
                    Queue<NotifySingleRpcTask> rpcQueue = new LinkedList<>();
                    
                    for (Member member : ipList) {
                        // grpc report data change only
                        rpcQueue.add(
                                new NotifySingleRpcTask(dataId, group, tenant, tag, dumpTs, evt.isBeta, member));
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

                        // grpc report data change only
                        try {
                            configClusterRpcClientProxy
                                    .syncConfigChange(member, syncRequest, new AsyncRpcNotifyCallBack(task));
                        } catch (Exception e) {
                            MetricsMonitor.getConfigNotifyException().increment();
                            asyncTaskExecute(task);
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
    
    private void asyncTaskExecute(NotifySingleRpcTask task) {
        int delay = getDelayTime(task);
        Queue<NotifySingleRpcTask> queue = new LinkedList<>();
        queue.add(task);
        AsyncRpcTask asyncTask = new AsyncRpcTask(queue);
        ConfigExecutor.scheduleAsyncNotify(asyncTask, delay, TimeUnit.MILLISECONDS);
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
                    task.getDataId(), task.getGroup(), task.getLastModified(), ex);
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
}
