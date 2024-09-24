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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.naming.HealthStateChangeTraceEvent;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.healthcheck.v2.PersistentHealthStatusSynchronizer;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Health check public methods for v2.x.
 *
 * <p>Current health check logic is same as v1.x. TODO refactor health check for v2.x.
 *
 * @author nkorange
 * @author xiweng.yy
 * @since 2.0.0
 */
@Component
public class HealthCheckCommonV2 {
    
    @Autowired
    private DistroMapper distroMapper;
    
    @Autowired
    private SwitchDomain switchDomain;
    
    @Autowired
    private PersistentHealthStatusSynchronizer healthStatusSynchronizer;
    
    /**
     * Re-evaluate check response time.
     *
     * @param checkRt check response time
     * @param task    health check task
     * @param params  health params
     */
    public void reEvaluateCheckRt(long checkRt, HealthCheckTaskV2 task, SwitchDomain.HealthParams params) {
        task.setCheckRtLast(checkRt);
        
        if (checkRt > task.getCheckRtWorst()) {
            task.setCheckRtWorst(checkRt);
        }
        
        if (checkRt < task.getCheckRtBest()) {
            task.setCheckRtBest(checkRt);
        }
        
        checkRt = (long) ((params.getFactor() * task.getCheckRtNormalized()) + (1 - params.getFactor()) * checkRt);
        
        if (checkRt > params.getMax()) {
            checkRt = params.getMax();
        }
        
        if (checkRt < params.getMin()) {
            checkRt = params.getMin();
        }
        
        task.setCheckRtNormalized(checkRt);
    }
    
    /**
     * Health check pass.
     *
     * @param task    health check task
     * @param service service
     * @param msg     message
     */
    public void checkOk(HealthCheckTaskV2 task, Service service, String msg) {
        try {
            HealthCheckInstancePublishInfo instance = (HealthCheckInstancePublishInfo) task.getClient()
                    .getInstancePublishInfo(service);
            if (instance == null) {
                return;
            }
            try {
                if (!instance.isHealthy()) {
                    String serviceName = service.getGroupedServiceName();
                    String clusterName = instance.getCluster();
                    if (instance.getOkCount().incrementAndGet() >= switchDomain.getCheckTimes()) {
                        if (switchDomain.isHealthCheckEnabled(serviceName) && !task.isCancelled() && distroMapper
                                .responsible(task.getClient().getResponsibleId())) {
                            healthStatusSynchronizer.instanceHealthStatusChange(true, task.getClient(), service, instance);
                            Loggers.EVT_LOG.info("serviceName: {} {POS} {IP-ENABLED} valid: {}:{}@{}, region: {}, msg: {}",
                                    serviceName, instance.getIp(), instance.getPort(), clusterName,
                                    UtilsAndCommons.LOCALHOST_SITE, msg);
                            NotifyCenter.publishEvent(new HealthStateChangeTraceEvent(System.currentTimeMillis(),
                                    service.getNamespace(), service.getGroup(), service.getName(), instance.getIp(),
                                    instance.getPort(), true, msg));
                        }
                    } else {
                        Loggers.EVT_LOG.info("serviceName: {} {OTHER} {IP-ENABLED} pre-valid: {}:{}@{} in {}, msg: {}",
                                serviceName, instance.getIp(), instance.getPort(), clusterName, instance.getOkCount(), msg);
                    }
                }
            } finally {
                instance.resetFailCount();
                instance.finishCheck();
            }
        } catch (Throwable t) {
            Loggers.SRV_LOG.error("[CHECK-OK] error when close check task.", t);
        }
    }
    
    /**
     * Health check fail, when instance check failed count more than max failed time, set unhealthy.
     *
     * @param task    health check task
     * @param service service
     * @param msg     message
     */
    public void checkFail(HealthCheckTaskV2 task, Service service, String msg) {
        try {
            HealthCheckInstancePublishInfo instance = (HealthCheckInstancePublishInfo) task.getClient()
                    .getInstancePublishInfo(service);
            if (instance == null) {
                return;
            }
            try {
                if (instance.isHealthy()) {
                    String serviceName = service.getGroupedServiceName();
                    String clusterName = instance.getCluster();
                    if (instance.getFailCount().incrementAndGet() >= switchDomain.getCheckTimes()) {
                        if (switchDomain.isHealthCheckEnabled(serviceName) && !task.isCancelled() && distroMapper
                                .responsible(task.getClient().getResponsibleId())) {
                            healthStatusSynchronizer.instanceHealthStatusChange(false, task.getClient(), service, instance);
                            Loggers.EVT_LOG
                                    .info("serviceName: {} {POS} {IP-DISABLED} invalid: {}:{}@{}, region: {}, msg: {}",
                                        serviceName, instance.getIp(), instance.getPort(), clusterName,
                                        UtilsAndCommons.LOCALHOST_SITE, msg);
                            NotifyCenter.publishEvent(new HealthStateChangeTraceEvent(System.currentTimeMillis(),
                                    service.getNamespace(), service.getGroup(), service.getName(), instance.getIp(),
                                    instance.getPort(), false, msg));
                        }
                    } else {
                        Loggers.EVT_LOG.info("serviceName: {} {OTHER} {IP-DISABLED} pre-invalid: {}:{}@{} in {}, msg: {}",
                                serviceName, instance.getIp(), instance.getPort(), clusterName, instance.getFailCount(),
                                msg);
                    }
                }
            } finally {
                instance.resetOkCount();
                instance.finishCheck();
            }
        } catch (Throwable t) {
            Loggers.SRV_LOG.error("[CHECK-FAIL] error when close check task.", t);
        }
    }
    
    /**
     * Health check fail, set instance unhealthy directly.
     *
     * @param task    health check task
     * @param service service
     * @param msg     message
     */
    public void checkFailNow(HealthCheckTaskV2 task, Service service, String msg) {
        try {
            HealthCheckInstancePublishInfo instance = (HealthCheckInstancePublishInfo) task.getClient()
                    .getInstancePublishInfo(service);
            if (null == instance) {
                return;
            }
            try {
                if (instance.isHealthy()) {
                    String serviceName = service.getGroupedServiceName();
                    String clusterName = instance.getCluster();
                    if (switchDomain.isHealthCheckEnabled(serviceName) && !task.isCancelled() && distroMapper
                            .responsible(task.getClient().getResponsibleId())) {
                        healthStatusSynchronizer.instanceHealthStatusChange(false, task.getClient(), service, instance);
                        Loggers.EVT_LOG.info("serviceName: {} {POS} {IP-DISABLED} invalid: {}:{}@{}, region: {}, msg: {}",
                                serviceName, instance.getIp(), instance.getPort(), clusterName,
                                UtilsAndCommons.LOCALHOST_SITE, msg);
                        NotifyCenter.publishEvent(new HealthStateChangeTraceEvent(System.currentTimeMillis(),
                                service.getNamespace(), service.getGroup(), service.getName(), instance.getIp(),
                                instance.getPort(), false, msg));
                    }
                }
            } finally {
                instance.resetOkCount();
                instance.finishCheck();
            }
        } catch (Throwable t) {
            Loggers.SRV_LOG.error("[CHECK-FAIL] error when close check task.", t);
        }
    }
}
