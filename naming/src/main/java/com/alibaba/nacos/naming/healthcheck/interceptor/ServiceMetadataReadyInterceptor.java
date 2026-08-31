/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.healthcheck.interceptor;

import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadataProcessor;
import com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl;
import com.alibaba.nacos.naming.healthcheck.NacosHealthCheckTask;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * Intercept persistent instance health checks until local snapshots are ready.
 *
 * @author Zhengcy05
 */
public class ServiceMetadataReadyInterceptor extends AbstractHealthCheckInterceptor {
    
    private volatile boolean healthCheckReleased;
    
    private volatile PersistentClientOperationServiceImpl persistentClientProcessor;
    
    private volatile ServiceMetadataProcessor serviceMetadataProcessor;
    
    @Override
    public boolean intercept(NacosHealthCheckTask object) {
        if (healthCheckReleased) {
            return false;
        }
        boolean applicationStarted = ApplicationUtils.isStarted();
        if (applicationStarted) {
            healthCheckReleased = true;
            return false;
        }
        try {
            initProcessorsIfNecessary();
            boolean persistentClientSnapshotLoaded = persistentClientProcessor.isSnapshotLoaded();
            boolean serviceMetadataSnapshotLoaded = serviceMetadataProcessor.isSnapshotLoaded();
            if (persistentClientSnapshotLoaded && serviceMetadataSnapshotLoaded) {
                healthCheckReleased = true;
                return false;
            }
            logSkippedTask(object, persistentClientSnapshotLoaded, serviceMetadataSnapshotLoaded,
                applicationStarted);
            return true;
        } catch (Exception e) {
            logSkippedTask(object, false, false, applicationStarted);
            return true;
        }
    }
    
    private void initProcessorsIfNecessary() {
        if (persistentClientProcessor != null && serviceMetadataProcessor != null) {
            return;
        }
        synchronized (this) {
            if (persistentClientProcessor == null) {
                persistentClientProcessor =
                    ApplicationUtils.getBean(PersistentClientOperationServiceImpl.class);
            }
            if (serviceMetadataProcessor == null) {
                serviceMetadataProcessor = ApplicationUtils.getBean(ServiceMetadataProcessor.class);
            }
        }
    }
    
    private void logSkippedTask(NacosHealthCheckTask task, boolean persistentClientSnapshotLoaded,
        boolean serviceMetadataSnapshotLoaded, boolean applicationStarted) {
        Loggers.EVT_LOG.info(
            "[HEALTH-CHECK] skip task {} because snapshots are not ready, persistentClientSnapshotLoaded={}, "
                + "serviceMetadataSnapshotLoaded={}, applicationStarted={}",
            task.getTaskId(), persistentClientSnapshotLoaded, serviceMetadataSnapshotLoaded,
            applicationStarted);
    }
    
    @Override
    public boolean isInterceptType(Class<?> type) {
        return HealthCheckTaskV2.class.isAssignableFrom(type);
    }
    
    @Override
    public int order() {
        return Integer.MIN_VALUE + 2;
    }
}
