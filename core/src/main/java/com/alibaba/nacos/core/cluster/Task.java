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

import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.NAsyncHttpClient;
import com.alibaba.nacos.core.cluster.task.ClusterConfSyncTask;
import com.alibaba.nacos.core.cluster.task.MemberShutdownTask;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.Loggers;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class Task implements Subscribe<IsolationEvent>, Runnable {

    protected volatile boolean shutdown = false;
    protected NAsyncHttpClient asyncHttpClient;
    protected ServerMemberManager memberManager;

    public Task(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
        this.asyncHttpClient = HttpClientManager.newAsyncHttpClient(ServerMemberManager.class.getCanonicalName());
        NotifyCenter.registerSubscribe(this);
    }

    @Override
    public void run() {
        if (shutdown) {
            return;
        }
        try {
            executeBody();
        } catch (Throwable t) {
            Loggers.CORE.error("this task execute has error : {}", t);
        } finally {
            if (!shutdown) {
                after();
            }
        }
    }

    // init some resource

    public void init() {

    }

    @Override
    public final void onEvent(IsolationEvent event) {
        // Execute the shutdown hook
        shutdown();
        // Execute this node logout logic
        Task task = new MemberShutdownTask(memberManager);
        task.executeBody();
    }

    @Override
    public final Class<? extends Event> subscribeType() {
        return IsolationEvent.class;
    }

    /**
     * Task executive
     */
    protected abstract void executeBody();

    /**
     * after executeBody should do
     */
    protected void after() {

    }

    public void shutdown() {
        shutdown = true;
    }

}
