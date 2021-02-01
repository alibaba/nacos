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

package com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteContent;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.ServiceChangeV1Task;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.Map;

/**
 * Async services check task for upgrading.
 *
 * @author xiweng.yy
 */
public class AsyncServicesCheckTask extends AbstractExecuteTask {
    
    private final DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine;
    
    private final UpgradeJudgement upgradeJudgement;
    
    public AsyncServicesCheckTask(DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine,
            UpgradeJudgement upgradeJudgement) {
        this.doubleWriteDelayTaskEngine = doubleWriteDelayTaskEngine;
        this.upgradeJudgement = upgradeJudgement;
    }
    
    @Override
    public void run() {
        if (upgradeJudgement.isUseGrpcFeatures()) {
            return;
        }
        try {
            ServiceManager serviceManager = ApplicationUtils.getBean(ServiceManager.class);
            ServiceStorage serviceStorage = ApplicationUtils.getBean(ServiceStorage.class);
            for (String each : serviceManager.getAllNamespaces()) {
                for (Map.Entry<String, Service> entry : serviceManager.chooseServiceMap(each).entrySet()) {
                    checkService(each, entry.getKey(), entry.getValue(), serviceStorage);
                }
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("async check for service error");
        }
    }
    
    private void checkService(String namespace, String fullServiceName, Service serviceV1,
            ServiceStorage serviceStorage) {
        if (upgradeJudgement.isUseGrpcFeatures()) {
            return;
        }
        String groupName = serviceV1.getGroupName();
        String serviceName = NamingUtils.getServiceName(fullServiceName);
        com.alibaba.nacos.naming.core.v2.pojo.Service serviceV2 = com.alibaba.nacos.naming.core.v2.pojo.Service
                .newService(namespace, groupName, serviceName);
        ServiceInfo serviceInfo = serviceStorage.getData(serviceV2);
        if (serviceV1.allIPs().size() != serviceInfo.getHosts().size()) {
            boolean isEphemeral = serviceV1.allIPs(false).isEmpty();
            String key = ServiceChangeV1Task.getKey(namespace, fullServiceName, isEphemeral);
            ServiceChangeV1Task task = new ServiceChangeV1Task(namespace, fullServiceName, isEphemeral,
                    DoubleWriteContent.INSTANCE);
            doubleWriteDelayTaskEngine.addTask(key, task);
        }
    }
}
