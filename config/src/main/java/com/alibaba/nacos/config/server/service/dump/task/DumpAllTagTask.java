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

package com.alibaba.nacos.config.server.service.dump.task;

import com.alibaba.nacos.config.server.manager.AbstractTask;

/**
 * Dump all tag task.
 *
 * @author Nacos
 * @date 2020/7/5 12:19 PM
 */
public class DumpAllTagTask extends AbstractTask {
    
    @Override
    public void merge(AbstractTask task) {
    }
    
    public static final String TASK_ID = "dumpAllTagConfigTask";
}
