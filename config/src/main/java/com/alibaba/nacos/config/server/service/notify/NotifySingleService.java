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

import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.config.server.utils.LogUtil;
import org.slf4j.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Notify Single server.
 *
 * @author Nacos
 */
public class NotifySingleService {
    private static final Logger LOGGER = LogUtil.FATAL_LOG;
    
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
                        target, getDataId(), getGroup(), getLastModified(), e);
            }
            
            if (!this.isSuccess) {
                LogUtil.NOTIFY_LOG
                        .error("[notify-retry] target:{} dataid:{} group:{} ts:{}", target, getDataId(), getGroup(),
                                getLastModified());
                try {
                    ((ScheduledThreadPoolExecutor) executor).schedule(this, 500L, TimeUnit.MILLISECONDS);
                } catch (Exception e) { // The notification failed, but at the same time, the node was offline
                    LOGGER.warn("[notify-thread-pool] cluster remove node {}, current thread was tear down.", target, e);
                }
            }
        }
    }
}
