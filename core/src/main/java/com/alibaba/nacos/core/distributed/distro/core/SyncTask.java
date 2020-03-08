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

package com.alibaba.nacos.core.distributed.distro.core;

import com.alibaba.nacos.core.notify.Event;
import java.util.List;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class SyncTask implements Event {

    private static final long serialVersionUID = -336027025084024030L;

    private String bizInfo;

    private List<String> keys;

    private int retryCount;

    private long lastExecuteTime;

    private String targetServer;

    public static SyncTaskBuilder builder() {
        return new SyncTaskBuilder();
    }

    public String getBizInfo() {
        return bizInfo;
    }

    public void setBizInfo(String bizInfo) {
        this.bizInfo = bizInfo;
    }

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getLastExecuteTime() {
        return lastExecuteTime;
    }

    public void setLastExecuteTime(long lastExecuteTime) {
        this.lastExecuteTime = lastExecuteTime;
    }

    public String getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(String targetServer) {
        this.targetServer = targetServer;
    }

    @Override
    public String toString() {
        return "SyncTask{" +
                "bizInfo='" + bizInfo + '\'' +
                ", keys=" + keys +
                ", retryCount=" + retryCount +
                ", lastExecuteTime=" + lastExecuteTime +
                ", targetServer='" + targetServer + '\'' +
                '}';
    }

    @Override
    public Class<? extends Event> eventType() {
        return SyncTask.class;
    }

    public static final class SyncTaskBuilder {
        private String bizInfo;
        private List<String> keys;
        private int retryCount;
        private long lastExecuteTime;
        private String targetServer;

        private SyncTaskBuilder() {
        }

        public SyncTaskBuilder bizInfo(String bizInfo) {
            this.bizInfo = bizInfo;
            return this;
        }

        public SyncTaskBuilder keys(List<String> keys) {
            this.keys = keys;
            return this;
        }

        public SyncTaskBuilder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public SyncTaskBuilder lastExecuteTime(long lastExecuteTime) {
            this.lastExecuteTime = lastExecuteTime;
            return this;
        }

        public SyncTaskBuilder targetServer(String targetServer) {
            this.targetServer = targetServer;
            return this;
        }

        public SyncTask build() {
            SyncTask syncTask = new SyncTask();
            syncTask.keys = this.keys;
            syncTask.targetServer = this.targetServer;
            syncTask.bizInfo = this.bizInfo;
            syncTask.retryCount = this.retryCount;
            syncTask.lastExecuteTime = this.lastExecuteTime;
            return syncTask;
        }
    }
}
