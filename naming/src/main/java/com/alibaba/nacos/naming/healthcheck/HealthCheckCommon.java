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

package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.PushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Health check public methods.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
@SuppressWarnings("PMD.ThreadPoolCreationRule")
public class HealthCheckCommon {
    
    @Autowired
    private DistroMapper distroMapper;
    
    @Autowired
    private SwitchDomain switchDomain;
    
    @Autowired
    private ServerMemberManager memberManager;
    
    @Autowired
    private PushService pushService;
    
    private static LinkedBlockingDeque<HealthCheckResult> healthCheckResults = new LinkedBlockingDeque<>(1024 * 128);
    
    /**
     * Init Health check.
     */
    public void init() {
        GlobalExecutor.scheduleNamingHealthCheck(() -> {
            List list = Arrays.asList(healthCheckResults.toArray());
            healthCheckResults.clear();
            
            Collection<Member> sameSiteServers = memberManager.allMembers();
            
            if (sameSiteServers == null || sameSiteServers.size() <= 0) {
                return;
            }
            
            for (Member server : sameSiteServers) {
                if (server.getAddress().equals(NetUtils.localServer())) {
                    continue;
                }
                Map<String, String> params = new HashMap<>(10);
                params.put("result", JacksonUtils.toJson(list));
                if (Loggers.SRV_LOG.isDebugEnabled()) {
                    Loggers.SRV_LOG.debug("[HEALTH-SYNC] server: {}, healthCheckResults: {}", server,
                            JacksonUtils.toJson(list));
                }
                
                RestResult<String> httpResult = HttpClient.httpPost(
                        "http://" + server.getAddress() + EnvUtil.getContextPath()
                                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/healthCheckResult", null, params);
                
                if (!httpResult.ok()) {
                    Loggers.EVT_LOG.warn("[HEALTH-CHECK-SYNC] failed to send result to {}, result: {}", server,
                            JacksonUtils.toJson(list));
                }
                
            }
            
        }, 500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Re-evaluate check responsce time.
     *
     * @param checkRT check response time
     * @param task    health check task
     * @param params  health params
     */
    public void reEvaluateCheckRT(long checkRT, HealthCheckTask task, SwitchDomain.HealthParams params) {
        task.setCheckRtLast(checkRT);
        
        if (checkRT > task.getCheckRtWorst()) {
            task.setCheckRtWorst(checkRT);
        }
        
        if (checkRT < task.getCheckRtBest()) {
            task.setCheckRtBest(checkRT);
        }
        
        checkRT = (long) ((params.getFactor() * task.getCheckRtNormalized()) + (1 - params.getFactor()) * checkRT);
        
        if (checkRT > params.getMax()) {
            checkRT = params.getMax();
        }
        
        if (checkRT < params.getMin()) {
            checkRT = params.getMin();
        }
        
        task.setCheckRtNormalized(checkRT);
    }
    
    /**
     * Health check pass.
     *
     * @param ip   instance
     * @param task health check task
     * @param msg  message
     */
    public void checkOK(Instance ip, HealthCheckTask task, String msg) {
        Cluster cluster = task.getCluster();
        
        try {
            if (!ip.isHealthy() || !ip.isMockValid()) {
                if (ip.getOkCount().incrementAndGet() >= switchDomain.getCheckTimes()) {
                    if (distroMapper.responsible(cluster, ip)) {
                        ip.setHealthy(true);
                        ip.setMockValid(true);
                        
                        Service service = cluster.getService();
                        service.setLastModifiedMillis(System.currentTimeMillis());
                        pushService.serviceChanged(service);
                        addResult(new HealthCheckResult(service.getName(), ip));
                        
                        Loggers.EVT_LOG.info("serviceName: {} {POS} {IP-ENABLED} valid: {}:{}@{}, region: {}, msg: {}",
                                cluster.getService().getName(), ip.getIp(), ip.getPort(), cluster.getName(),
                                UtilsAndCommons.LOCALHOST_SITE, msg);
                    } else {
                        if (!ip.isMockValid()) {
                            ip.setMockValid(true);
                            Loggers.EVT_LOG
                                    .info("serviceName: {} {PROBE} {IP-ENABLED} valid: {}:{}@{}, region: {}, msg: {}",
                                            cluster.getService().getName(), ip.getIp(), ip.getPort(), cluster.getName(),
                                            UtilsAndCommons.LOCALHOST_SITE, msg);
                        }
                    }
                } else {
                    Loggers.EVT_LOG.info("serviceName: {} {OTHER} {IP-ENABLED} pre-valid: {}:{}@{} in {}, msg: {}",
                            cluster.getService().getName(), ip.getIp(), ip.getPort(), cluster.getName(),
                            ip.getOkCount(), msg);
                }
            }
        } catch (Throwable t) {
            Loggers.SRV_LOG.error("[CHECK-OK] error when close check task.", t);
        }
        
        ip.getFailCount().set(0);
        ip.setBeingChecked(false);
    }
    
    /**
     * Health check fail, when instance check failed count more than max failed time, set unhealthy.
     *
     * @param ip   instance
     * @param task health check task
     * @param msg  message
     */
    public void checkFail(Instance ip, HealthCheckTask task, String msg) {
        Cluster cluster = task.getCluster();
        
        try {
            if (ip.isHealthy() || ip.isMockValid()) {
                if (ip.getFailCount().incrementAndGet() >= switchDomain.getCheckTimes()) {
                    if (distroMapper.responsible(cluster, ip)) {
                        ip.setHealthy(false);
                        ip.setMockValid(false);
                        
                        Service service = cluster.getService();
                        service.setLastModifiedMillis(System.currentTimeMillis());
                        addResult(new HealthCheckResult(service.getName(), ip));
                        
                        pushService.serviceChanged(service);
                        
                        Loggers.EVT_LOG
                                .info("serviceName: {} {POS} {IP-DISABLED} invalid: {}:{}@{}, region: {}, msg: {}",
                                        cluster.getService().getName(), ip.getIp(), ip.getPort(), cluster.getName(),
                                        UtilsAndCommons.LOCALHOST_SITE, msg);
                    } else {
                        Loggers.EVT_LOG
                                .info("serviceName: {} {PROBE} {IP-DISABLED} invalid: {}:{}@{}, region: {}, msg: {}",
                                        cluster.getService().getName(), ip.getIp(), ip.getPort(), cluster.getName(),
                                        UtilsAndCommons.LOCALHOST_SITE, msg);
                    }
                    
                } else {
                    Loggers.EVT_LOG.info("serviceName: {} {OTHER} {IP-DISABLED} pre-invalid: {}:{}@{} in {}, msg: {}",
                            cluster.getService().getName(), ip.getIp(), ip.getPort(), cluster.getName(),
                            ip.getFailCount(), msg);
                }
            }
        } catch (Throwable t) {
            Loggers.SRV_LOG.error("[CHECK-FAIL] error when close check task.", t);
        }
        
        ip.getOkCount().set(0);
        
        ip.setBeingChecked(false);
    }
    
    /**
     * Health check fail, set instance unhealthy directly.
     *
     * @param ip   instance
     * @param task health check task
     * @param msg  message
     */
    public void checkFailNow(Instance ip, HealthCheckTask task, String msg) {
        Cluster cluster = task.getCluster();
        try {
            if (ip.isHealthy() || ip.isMockValid()) {
                if (distroMapper.responsible(cluster, ip)) {
                    ip.setHealthy(false);
                    ip.setMockValid(false);
                    
                    Service service = cluster.getService();
                    service.setLastModifiedMillis(System.currentTimeMillis());
                    
                    pushService.serviceChanged(service);
                    addResult(new HealthCheckResult(service.getName(), ip));
                    
                    Loggers.EVT_LOG
                            .info("serviceName: {} {POS} {IP-DISABLED} invalid-now: {}:{}@{}, region: {}, msg: {}",
                                    cluster.getService().getName(), ip.getIp(), ip.getPort(), cluster.getName(),
                                    UtilsAndCommons.LOCALHOST_SITE, msg);
                } else {
                    if (ip.isMockValid()) {
                        ip.setMockValid(false);
                        Loggers.EVT_LOG
                                .info("serviceName: {} {PROBE} {IP-DISABLED} invalid-now: {}:{}@{}, region: {}, msg: {}",
                                        cluster.getService().getName(), ip.getIp(), ip.getPort(), cluster.getName(),
                                        UtilsAndCommons.LOCALHOST_SITE, msg);
                    }
                    
                }
            }
        } catch (Throwable t) {
            Loggers.SRV_LOG.error("[CHECK-FAIL-NOW] error when close check task.", t);
        }
        
        ip.getOkCount().set(0);
        ip.setBeingChecked(false);
    }
    
    private void addResult(HealthCheckResult result) {
        
        if (!switchDomain.getIncrementalList().contains(result.getServiceName())) {
            return;
        }
        
        if (!healthCheckResults.offer(result)) {
            Loggers.EVT_LOG.warn("[HEALTH-CHECK-SYNC] failed to add check result to queue, queue size: {}",
                    healthCheckResults.size());
        }
    }
    
    static class HealthCheckResult {
        
        private String serviceName;
        
        private Instance instance;
        
        public HealthCheckResult(String serviceName, Instance instance) {
            this.serviceName = serviceName;
            this.instance = instance;
        }
        
        public String getServiceName() {
            return serviceName;
        }
        
        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
        
        public Instance getInstance() {
            return instance;
        }
        
        public void setInstance(Instance instance) {
            this.instance = instance;
        }
    }
}
