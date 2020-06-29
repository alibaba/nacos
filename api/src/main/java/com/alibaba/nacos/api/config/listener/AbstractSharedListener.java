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

package com.alibaba.nacos.api.config.listener;

import java.util.concurrent.Executor;

/**
 * Shared Listener.
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class AbstractSharedListener implements Listener {
    
    private volatile String dataId;
    
    private volatile String group;
    
    public final void fillContext(String dataId, String group) {
        this.dataId = dataId;
        this.group = group;
    }
    
    @Override
    public final void receiveConfigInfo(String configInfo) {
        innerReceive(dataId, group, configInfo);
    }
    
    @Override
    public Executor getExecutor() {
        return null;
    }
    
    /**
     * receive.
     *
     * @param dataId     data ID
     * @param group      group
     * @param configInfo content
     */
    public abstract void innerReceive(String dataId, String group, String configInfo);
}
