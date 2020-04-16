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

package com.alibaba.nacos.core.cluster.lookup;

import com.alibaba.nacos.core.cluster.IsolationEvent;
import com.alibaba.nacos.core.cluster.RecoverEvent;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.SmartSubscribe;
import com.alibaba.nacos.core.utils.Loggers;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
abstract class Task extends SmartSubscribe implements Runnable {

    protected volatile boolean shutdown = false;
    private volatile boolean skip = false;

    public Task() {
        NotifyCenter.registerSubscribe(this);
    }

    @Override
    public void run() {
        if (shutdown || skip) {
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

    @Override
    public final void onEvent(Event event) {
        if (event instanceof IsolationEvent) {
            // Trigger task ignore
            skip = true;
            return;
        }
        if (event instanceof RecoverEvent) {
            skip = false;
        }
    }

    @Override
    public boolean canNotify(Event event) {
        if (event instanceof IsolationEvent) {
            return true;
        }
        if (event instanceof RecoverEvent) {
            return true;
        }
        return false;
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
