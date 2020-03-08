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

package com.alibaba.nacos.core.cluster;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class Task implements Runnable {

    protected ServerMemberManager nodeManager;
    private boolean inExecute = false;

    void setNodeManager(ServerMemberManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    @Override
    public void run() {
        inExecute = true;
        executeBody();
    }

    // init some resource

    protected void init() {

    }

    /**
     * Task executive
     */
    protected abstract void executeBody();

    public boolean isInExecute() {
        return inExecute;
    }

    /**
     * Scheduled information
     *
     * @return {@link TaskInfo}
     */
    public TaskInfo scheduleInfo() {
        return TaskInfo.DEFAULT;
    }

    /**
     * task types
     * 1：Need to execute regularly
     * 2：Need to delay execution
     * 3：Execute immediately
     *
     * @return {@link TaskType}
     */
    public abstract TaskType[] types();

    public static enum TaskType {

        /**
         * Need to execute regularly
         */
        SCHEDULE_TASK(1),

        /**
         * Need to delay execution
         */
        DELAY_TASK(2),

        /**
         * Execute immediately
         */
        IMMEDIATELY_TASK(3),

        /**
         * Execute by current thread
         */
        NOW_THREAD(4);

        private int type;

        TaskType(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }
    }

    public static class TaskInfo {

        public static final TaskInfo DEFAULT = new TaskInfo(0L, 30L, TimeUnit.SECONDS);

        private final long delay;
        private final long period;
        private final TimeUnit unit;

        public TaskInfo(long delay, long period, TimeUnit unit) {
            this.delay = delay;
            this.period = period;
            this.unit = unit;
        }

        public long getDelay() {
            return delay;
        }

        public long getPeriod() {
            return period;
        }

        public TimeUnit getUnit() {
            return unit;
        }

    }

}
