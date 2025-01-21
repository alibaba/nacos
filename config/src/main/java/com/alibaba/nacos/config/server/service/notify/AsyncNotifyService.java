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
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.remote.ConfigClusterRpcClientProxy;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.core.cluster.MemberMetaDataConstants.SUPPORT_GRAY_MODEL;

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
    private ConfigClusterRpcClientProxy configClusterRpcClientProxy;
    
    private ServerMemberManager memberManager;
    
    static final List<NodeState> HEALTHY_CHECK_STATUS = new ArrayList<>();
    
    static {
        HEALTHY_CHECK_STATUS.add(NodeState.UP);
        HEALTHY_CHECK_STATUS.add(NodeState.SUSPICIOUS);
    }
    
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
                handleConfigDataChangeEvent(event);
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
    }
    
    void handleConfigDataChangeEvent(Event event) {
        if (event instanceof ConfigDataChangeEvent) {
            ConfigDataChangeEvent evt = (ConfigDataChangeEvent) event;
            
            MetricsMonitor.incrementConfigChangeCount(evt.tenant, evt.group, evt.dataId);
            
            Collection<Member> ipList = memberManager.allMembersWithoutSelf();
            
            // In fact, any type of queue here can be
            Queue<NotifySingleRpcTask> rpcQueue = new LinkedList<>();
            
            for (Member member : ipList) {
                // grpc report data change only
                NotifySingleRpcTask notifySingleRpcTask = generateTask(evt, member);
                if (notifySingleRpcTask != null) {
                    rpcQueue.add(notifySingleRpcTask);
                }
                
            }
            if (!rpcQueue.isEmpty()) {
                ConfigExecutor.executeAsyncNotify(new AsyncRpcTask(rpcQueue));
            }
        }
    }
    
    private NotifySingleRpcTask generateTask(ConfigDataChangeEvent configDataChangeEvent, Member member) {
        
        NotifySingleRpcTask task = new NotifySingleRpcTask(configDataChangeEvent.dataId, configDataChangeEvent.group,
                configDataChangeEvent.tenant, configDataChangeEvent.grayName, configDataChangeEvent.lastModifiedTs,
                member);
        
        if (PropertyUtil.isGrayCompatibleModel() && StringUtils.isNotBlank(configDataChangeEvent.grayName)) {
            
            // old server should set beta or tag flag
            if (!(Boolean) member.getExtendInfo().getOrDefault(SUPPORT_GRAY_MODEL, Boolean.FALSE)) {
                String underLine = "_";
                task.setBeta(BetaGrayRule.TYPE_BETA.equals(configDataChangeEvent.grayName));
                if (configDataChangeEvent.grayName.startsWith(TagGrayRule.TYPE_TAG + underLine)) {
                    task.setTag(configDataChangeEvent.grayName.substring(
                            configDataChangeEvent.grayName.indexOf(TagGrayRule.TYPE_TAG + underLine) + 4));
                }
                
            }
        }
        
        // compatible with gray model
        return task;
    }
    
    private boolean isUnHealthy(String targetIp) {
        return !memberManager.stateCheck(targetIp, HEALTHY_CHECK_STATUS);
    }
    
    void executeAsyncRpcTask(Queue<NotifySingleRpcTask> queue) {
        while (!queue.isEmpty()) {
            NotifySingleRpcTask task = queue.poll();
            
            ConfigChangeClusterSyncRequest syncRequest = new ConfigChangeClusterSyncRequest();
            syncRequest.setDataId(task.getDataId());
            syncRequest.setTenant(task.getTenant());
            syncRequest.setGroup(task.getGroup());
            syncRequest.setLastModified(task.getLastModified());
            syncRequest.setGrayName(task.getGrayName());
            syncRequest.setBeta(task.isBeta());
            syncRequest.setTag(task.getTag());
            Member member = task.member;
            
            String event = getNotifyEvent(task);
            if (memberManager.hasMember(member.getAddress())) {
                // start the health check and there are ips that are not monitored, put them directly in the notification queue, otherwise notify
                boolean unHealthNeedDelay = isUnHealthy(member.getAddress());
                if (unHealthNeedDelay) {
                    // target ip is unhealthy, then put it in the notification list
                    ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                            task.getLastModified(), InetUtils.getSelfIP(), event,
                            ConfigTraceService.NOTIFY_TYPE_UNHEALTH, 0, member.getAddress());
                    // get delay time and set fail count to the task
                    asyncTaskExecute(task);
                } else {
                    
                    // grpc report data change only
                    try {
                        configClusterRpcClientProxy.syncConfigChange(member, syncRequest,
                                new AsyncRpcNotifyCallBack(AsyncNotifyService.this, task));
                    } catch (Exception e) {
                        MetricsMonitor.getConfigNotifyException().increment();
                        asyncTaskExecute(task);
                    }
                    
                }
            } else {
                //No nothing if  member has offline.
            }
            
        }
    }
    
    public class AsyncRpcTask implements Runnable {
        
        private Queue<NotifySingleRpcTask> queue;
        
        public AsyncRpcTask(Queue<NotifySingleRpcTask> queue) {
            this.queue = queue;
        }
        
        @Override
        public void run() {
            executeAsyncRpcTask(queue);
        }
    }
    
    public static class NotifySingleRpcTask extends AbstractDelayTask {
        
        private String dataId;
        
        private String group;
        
        private String tenant;
        
        private long lastModified;
        
        private int failCount;
        
        private Member member;
        
        private String grayName;
        
        @Deprecated
        private boolean isBeta;
        
        @Deprecated
        private String tag;
        
        public NotifySingleRpcTask(String dataId, String group, String tenant, String grayName, long lastModified,
                Member member) {
            this.dataId = dataId;
            this.group = group;
            this.tenant = tenant;
            this.lastModified = lastModified;
            this.member = member;
            this.grayName = grayName;
            setTaskInterval(3000L);
            
        }
        
        public boolean isBeta() {
            return isBeta;
        }
        
        public void setBeta(boolean beta) {
            isBeta = beta;
        }
        
        public String getTag() {
            return tag;
        }
        
        public void setTag(String tag) {
            this.tag = tag;
        }
        
        public String getGrayName() {
            return grayName;
        }
        
        public void setGrayName(String grayName) {
            this.grayName = grayName;
        }
        
        public String getDataId() {
            return dataId;
        }
        
        public String getGroup() {
            return group;
        }
        
        public int getFailCount() {
            return failCount;
        }
        
        public void setFailCount(int failCount) {
            this.failCount = failCount;
        }
        
        public long getLastModified() {
            return lastModified;
        }
        
        @Override
        public void merge(AbstractDelayTask task) {
            // Perform merge, but do nothing, tasks with the same dataId and group, later will replace the previous
            
        }
        
        public String getTenant() {
            return tenant;
        }
        
    }
    
    private void asyncTaskExecute(NotifySingleRpcTask task) {
        int delay = getDelayTime(task);
        Queue<NotifySingleRpcTask> queue = new LinkedList<>();
        queue.add(task);
        AsyncRpcTask asyncTask = new AsyncRpcTask(queue);
        ConfigExecutor.scheduleAsyncNotify(asyncTask, delay, TimeUnit.MILLISECONDS);
    }
    
    private static String getNotifyEvent(NotifySingleRpcTask task) {
        String event = ConfigTraceService.NOTIFY_EVENT;
        if (task.isBeta()) {
            event = ConfigTraceService.NOTIFY_EVENT_BETA;
        } else if (!StringUtils.isBlank(task.tag)) {
            event = ConfigTraceService.NOTIFY_EVENT_TAG + "-" + task.tag;
        } else if (StringUtils.isNotBlank(task.grayName)) {
            event = ConfigTraceService.NOTIFY_EVENT + "-" + task.grayName;
        }
        return event;
    }
    
    public static class AsyncRpcNotifyCallBack implements RequestCallBack<ConfigChangeClusterSyncResponse> {
        
        private NotifySingleRpcTask task;
        
        AsyncNotifyService asyncNotifyService;
        
        public AsyncRpcNotifyCallBack(AsyncNotifyService asyncNotifyService, NotifySingleRpcTask task) {
            this.task = task;
            this.asyncNotifyService = asyncNotifyService;
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
            String event = getNotifyEvent(task);
            
            long delayed = System.currentTimeMillis() - task.getLastModified();
            if (response.isSuccess()) {
                ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                        task.getLastModified(), InetUtils.getSelfIP(), event, ConfigTraceService.NOTIFY_TYPE_OK,
                        delayed, task.member.getAddress());
            } else {
                LOGGER.error("[notify-error] target:{} dataId:{} group:{} ts:{} code:{}", task.member.getAddress(),
                        task.getDataId(), task.getGroup(), task.getLastModified(), response.getErrorCode());
                ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                        task.getLastModified(), InetUtils.getSelfIP(), event, ConfigTraceService.NOTIFY_TYPE_ERROR,
                        delayed, task.member.getAddress());
                
                //get delay time and set fail count to the task
                asyncNotifyService.asyncTaskExecute(task);
                
                LogUtil.NOTIFY_LOG.error("[notify-retry] target:{} dataId:{} group:{} ts:{}", task.member.getAddress(),
                        task.getDataId(), task.getGroup(), task.getLastModified());
                
                MetricsMonitor.getConfigNotifyException().increment();
            }
        }
        
        @Override
        public void onException(Throwable ex) {
            String event = getNotifyEvent(task);
            
            long delayed = System.currentTimeMillis() - task.getLastModified();
            LOGGER.error("[notify-exception] target:{} dataId:{} group:{} ts:{} ex:{}", task.member.getAddress(),
                    task.getDataId(), task.getGroup(), task.getLastModified(), ex);
            ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                    task.getLastModified(), InetUtils.getSelfIP(), event, ConfigTraceService.NOTIFY_TYPE_EXCEPTION,
                    delayed, task.member.getAddress());
            
            //get delay time and set fail count to the task
            asyncNotifyService.asyncTaskExecute(task);
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
    private static int getDelayTime(NotifySingleRpcTask task) {
        int failCount = task.getFailCount();
        int delay = MIN_RETRY_INTERVAL + failCount * failCount * INCREASE_STEPS;
        if (failCount <= MAX_COUNT) {
            task.setFailCount(failCount + 1);
        }
        return delay;
    }
}
