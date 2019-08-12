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

import com.alibaba.nacos.config.server.manager.AbstractTask;
import com.alibaba.nacos.config.server.service.ServerListService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Notify Single server
 *
 * @author Nacos
 */
public class NotifySingleService {

    static class NotifyTaskProcessorWrapper extends NotifyTaskProcessor {

        public NotifyTaskProcessorWrapper() {
            /**
             *  serverListManager在这里没有用了
             */
            super(null);
        }

        @Override
        public boolean process(String taskType, AbstractTask task) {
            NotifySingleTask notifyTask = (NotifySingleTask)task;
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
                this.isSuccess = PROCESSOR.process(GroupKey2.getKey(getDataId(), getGroup()), this);
            } catch (Exception e) { // never goes here, but in case (运行中never中断此通知线程)
                this.isSuccess = false;
                LogUtil.notifyLog.error("[notify-exception] target:{} dataid:{} group:{} ts:{}", target, getDataId(),
                    getGroup(), getLastModified());
                LogUtil.notifyLog.debug("[notify-exception] target:{} dataid:{} group:{} ts:{}",
                    new Object[] {target, getDataId(), getGroup(), getLastModified()}, e);
            }

            if (!this.isSuccess) {
                LogUtil.notifyLog.error("[notify-retry] target:{} dataid:{} group:{} ts:{}", target, getDataId(),
                    getGroup(), getLastModified());
                try {
                    ((ScheduledThreadPoolExecutor)executor).schedule(this, 500L, TimeUnit.MILLISECONDS);
                } catch (Exception e) { // 通知虽然失败，但是同时此前节点也下线了
                    logger.warn("[notify-thread-pool] cluster remove node {}, current thread was tear down.", target,
                        e);
                }
            }
        }
    }

    static class NotifyThreadFactory implements ThreadFactory {
        private final String notifyTarget;

        NotifyThreadFactory(String notifyTarget) {
            this.notifyTarget = notifyTarget;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "com.alibaba.nacos.NotifySingleServiceThread-" + notifyTarget);
            thread.setDaemon(true);
            return thread;
        }
    }

    @Autowired
    public NotifySingleService(ServerListService serverListService) {
        this.serverListService = serverListService;
        setupNotifyExecutors();
    }

    /**
     * 系统启动时 or 集群扩容、下线时：单线程setupNotifyExecutors executors使用ConcurrentHashMap目的在于保证可见性
     */
    private void setupNotifyExecutors() {
        List<String> clusterIps = serverListService.getServerList();

        for (String ip : clusterIps) {
            // 固定线程数，无界队列（基于假设: 线程池的吞吐量不错，不会出现持续任务堆积，存在偶尔的瞬间压力）
            @SuppressWarnings("PMD.ThreadPoolCreationRule")
            Executor executor = Executors.newScheduledThreadPool(1, new NotifyThreadFactory(ip));

            if (null == executors.putIfAbsent(ip, executor)) {
                logger.warn("[notify-thread-pool] setup thread target ip {} ok.", ip);
            }
        }

        for (Map.Entry<String, Executor> entry : executors.entrySet()) {
            String target = entry.getKey();
            /**
             *  集群节点下线
             */
            if (!clusterIps.contains(target)) {
                ThreadPoolExecutor executor = (ThreadPoolExecutor)entry.getValue();
                executor.shutdown();
                executors.remove(target);
                logger.warn("[notify-thread-pool] tear down thread target ip {} ok.", target);
            }
        }

    }

    private final static Logger logger = LogUtil.fatalLog;

    private ServerListService serverListService;

    private ConcurrentHashMap<String, Executor> executors = new ConcurrentHashMap<String, Executor>();

    public ConcurrentHashMap<String, Executor> getExecutors() {
        return executors;
    }
}
