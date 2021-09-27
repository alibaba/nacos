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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.common.task.engine.NacosExecuteTaskExecuteEngine;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Naming execute task dispatcher.
 *
 * @author xiweng.yy
 */
public class NamingExecuteTaskDispatcher {
    
    private static final NamingExecuteTaskDispatcher INSTANCE = new NamingExecuteTaskDispatcher();
    
    private final NacosExecuteTaskExecuteEngine executeEngine;
    
    private NamingExecuteTaskDispatcher() {
        executeEngine = new NacosExecuteTaskExecuteEngine(EnvUtil.FUNCTION_MODE_NAMING, Loggers.SRV_LOG);
    }
    
    public static NamingExecuteTaskDispatcher getInstance() {
        return INSTANCE;
    }
    
    public void dispatchAndExecuteTask(Object dispatchTag, AbstractExecuteTask task) {
        executeEngine.addTask(dispatchTag, task);
    }
    
    public String workersStatus() {
        return executeEngine.workersStatus();
    }
}
