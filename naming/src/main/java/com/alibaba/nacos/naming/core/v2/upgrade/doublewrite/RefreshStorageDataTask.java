/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.upgrade.doublewrite;

import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * Refresh service storage cache data when upgrading.
 *
 * @author xiweng.yy
 */
public class RefreshStorageDataTask extends AbstractExecuteTask {
    
    private final Service service;
    
    public RefreshStorageDataTask(Service service) {
        this.service = service;
    }
    
    @Override
    public void run() {
        ApplicationUtils.getBean(ServiceStorage.class).getPushData(service);
    }
}
