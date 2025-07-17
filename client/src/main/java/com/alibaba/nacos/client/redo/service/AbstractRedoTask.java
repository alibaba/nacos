/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.redo.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import org.slf4j.Logger;

/**
 * Nacos client abstract redo task.
 *
 * @author xiweng.yy
 */
public abstract class AbstractRedoTask<S extends AbstractRedoService> extends AbstractExecuteTask {
    
    private final Logger logger;
    
    private final S redoService;
    
    public AbstractRedoTask(Logger logger, S redoService) {
        this.logger = logger;
        this.redoService = redoService;
    }
    
    @Override
    public void run() {
        if (!redoService.isConnected()) {
            logger.warn("Grpc Connection is disconnect, skip current redo task");
            return;
        }
        try {
            redoData();
        } catch (Exception e) {
            logger.warn("Redo task run with unexpected exception: ", e);
        }
    }
    
    /**
     * Do actual redo task.
     *
     * @throws NacosException if redo task failed.
     */
    protected abstract void redoData() throws NacosException;
    
    protected S getRedoService() {
        return redoService;
    }
}
