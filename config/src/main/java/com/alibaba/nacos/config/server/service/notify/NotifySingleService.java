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

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Notify Single server.
 *
 * @author Nacos
 */
public class NotifySingleService {
    
    static class NotifyTaskProcessorWrapper extends NotifyTaskProcessor {
        
        public NotifyTaskProcessorWrapper() {
            /*
             *  serverListManager is useless here
             */
            super(null);
        }
        
        @Override
        public boolean process(NacosTask task) {
            NotifySingleTask notifyTask = (NotifySingleTask) task;
            return notifyToDump(notifyTask.getDataId(), notifyTask.getGroup(), notifyTask.getTenant(),
                    notifyTask.getLastModified(), notifyTask.target);
        }
    }
    
    static class NotifySingleTask extends NotifyTask implements Runnable {
        
        private static final NotifyTaskProcessorWrapper PROCESSOR = new NotifyTaskProcessorWrapper();
        
        private final Executor executor;
        
        private final String target;
        
        private boolean isSuccess = false;
        
        public NotifySingleTask(String dataId, String group, String tenant, long lastModified, String target,
                Executor executor) {
            super(dataId, group, tenant, lastModified);
            this.target = target;
            this.executor = executor;
        }
        
        @Override
        public void run() {
            try {
                this.isSuccess = PROCESSOR.process(this);
            } catch (Exception e) { // never goes here, but in case (never interrupts this notification thread)
                this.isSuccess = false;
                LogUtil.NOTIFY_LOG
                        .error("[notify-exception] target:{} dataid:{} group:{} ts:{}", target, getDataId(), getGroup(),
                                getLastModified());
                LogUtil.NOTIFY_LOG.debug("[notify-exception] target:{} dataid:{} group:{} ts:{}",
                        new Object[] {target, getDataId(), getGroup(), getLastModified()}, e);
            }
            
            if (!this.isSuccess) {
                LogUtil.NOTIFY_LOG
                        .error("[notify-retry] target:{} dataid:{} group:{} ts:{}", target, getDataId(), getGroup(),
                                getLastModified());
                try {
                    ((ScheduledThreadPoolExecutor) executor).schedule(this, 500L, TimeUnit.MILLISECONDS);
                } catch (Exception e) { // The notification failed, but at the same time, the node was offline
                    LOGGER.warn("[notify-thread-pool] cluster remove node {}, current thread was tear down.", target,
                            e);
                }
            }
        }
    }
    
    @Autowired
    public NotifySingleService(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
        setupNotifyExecutors();
    }
    
    /**
     * When the system is started or when the cluster is expanded or offline: single-threaded setupNotifyExecutors
     * executors use ConcurrentHashMap to ensure visibility.
     */
    private void setupNotifyExecutors() {
        Collection<Member> clusterIps = memberManager.allMembers();
        
        for (Member member : clusterIps) {
            
            final String address = member.getAddress();
            
            /*
             * Fixed number of threads, unbounded queue
             * (based on assumption: thread pool throughput is good,
             * there will be no continuous task accumulation,
             * there is occasional instantaneous pressure)
             */
            Executor executor = ExecutorFactory.newSingleScheduledExecutorService(
                    new NameThreadFactory("com.alibaba.nacos.config.NotifySingleServiceThread-" + address));
            
            if (null == executors.putIfAbsent(address, executor)) {
                LOGGER.warn("[notify-thread-pool] setup thread target ip {} ok.", address);
            }
        }
        
        for (Map.Entry<String, Executor> entry : executors.entrySet()) {
            String target = entry.getKey();
            
            // The cluster node goes offline
            if (!clusterIps.contains(target)) {
                ThreadPoolExecutor executor = (ThreadPoolExecutor) entry.getValue();
                executor.shutdown();
                executors.remove(target);
                LOGGER.warn("[notify-thread-pool] tear down thread target ip {} ok.", target);
            }
        }
        
    }
    
    private static final Logger LOGGER = LogUtil.FATAL_LOG;
    
    private ServerMemberManager memberManager;
    
    private ConcurrentHashMap<String, Executor> executors = new ConcurrentHashMap<String, Executor>();
    
    public ConcurrentHashMap<String, Executor> getExecutors() {
        return executors;
    }
}
