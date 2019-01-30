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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.push.PushService;
import org.apache.commons.lang3.StringUtils;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author nacos
 */
public abstract class AbstractHealthCheckProcessor {

    private static final String HTTP_CHECK_MSG_PREFIX = "http:";

    static class HealthCheckResult {
        private String dom;
        private IpAddress ipAddress;

        public HealthCheckResult(String dom, IpAddress ipAddress) {
            this.dom = dom;
            this.ipAddress = ipAddress;
        }

        public String getDom() {
            return dom;
        }

        public void setDom(String dom) {
            this.dom = dom;
        }

        public IpAddress getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(IpAddress ipAddress) {
            this.ipAddress = ipAddress;
        }
    }

    public static final int CONNECT_TIMEOUT_MS = 500;
    private static LinkedBlockingDeque<HealthCheckResult> healthCheckResults = new LinkedBlockingDeque<>(1024 * 128);

    private void addResult(HealthCheckResult result) {

        if (!Switch.getIncrementalList().contains(result.getDom())) {
            return;
        }

        if (!healthCheckResults.offer(result)) {
            Loggers.EVT_LOG.warn("[HEALTH-CHECK-SYNC] failed to add check result to queue, queue size: {}", healthCheckResults.size());
        }
    }

    private static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("com.taobao.health-check.notifier");
            return thread;
        }
    });


    static {
        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                List list = Arrays.asList(healthCheckResults.toArray());
                healthCheckResults.clear();

                List<String> sameSiteServers = NamingProxy.getSameSiteServers().get("sameSite");

                if (sameSiteServers == null || sameSiteServers.size() <= 0 || !NamingProxy.getServers().contains(NetUtils.localServer())) {
                    return;
                }

                for (String server : sameSiteServers) {
                    if (server.equals(NetUtils.localServer())) {
                        continue;
                    }
                    Map<String, String> params = new HashMap<>(10);
                    params.put("result", JSON.toJSONString(list));
                    if (Loggers.DEBUG_LOG.isDebugEnabled()) {
                        Loggers.DEBUG_LOG.debug("[HEALTH-SYNC] server: {}, healthCheckResults: {}",
                            server, JSON.toJSONString(list));
                    }
                    if (!server.contains(":")) {
                        server = server + ":" + RunningConfig.getServerPort();
                    }
                    HttpClient.HttpResult httpResult = HttpClient.httpPost("http://" + server
                        + RunningConfig.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT
                        + "/api/healthCheckResult", null, params);

                    if (httpResult.code != HttpURLConnection.HTTP_OK) {
                        Loggers.EVT_LOG.warn("[HEALTH-CHECK-SYNC] failed to send result to {}, result: {}",
                            server, JSON.toJSONString(list));
                    }

                }

            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Run check task for domain
     *
     * @param task check task
     */
    public abstract void process(HealthCheckTask task);

    /**
     * Get check task type, refer to enum HealthCheckType
     *
     * @return check type
     */
    public abstract String getType();

    public static final HttpHealthCheckProcessor HTTP_PROCESSOR = new HttpHealthCheckProcessor();
    public static final TcpSuperSenseProcessor TCP_PROCESSOR = new TcpSuperSenseProcessor();
    public static final MysqlHealthCheckProcessor MYSQL_PROCESSOR = new MysqlHealthCheckProcessor();

    public static AbstractHealthCheckProcessor getProcessor(AbstractHealthChecker config) {
        if (config == null || StringUtils.isEmpty(config.getType())) {
            throw new IllegalArgumentException("empty check type");
        }

        if (config.getType().equals(HTTP_PROCESSOR.getType())) {
            return HTTP_PROCESSOR;
        }

        if (config.getType().equals(TCP_PROCESSOR.getType())) {
            return TCP_PROCESSOR;
        }

        if (config.getType().equals(MYSQL_PROCESSOR.getType())) {
            return MYSQL_PROCESSOR;
        }

        throw new IllegalArgumentException("Unknown check type: " + config.getType());
    }

    protected boolean isHealthCheckEnabled(VirtualClusterDomain virtualClusterDomain) {
        if (virtualClusterDomain.getEnableClientBeat()) {
            return false;
        }

        return virtualClusterDomain.getEnableHealthCheck();
    }

    protected void reEvaluateCheckRT(long checkRT, HealthCheckTask task, SwitchDomain.HealthParams params) {
        task.setCheckRTLast(checkRT);

        if (checkRT > task.getCheckRTWorst()) {
            task.setCheckRTWorst(checkRT);
        }

        if (checkRT < task.getCheckRTBest()) {
            task.setCheckRTBest(checkRT);
        }

        checkRT = (long) ((params.getFactor() * task.getCheckRTNormalized()) + (1 - params.getFactor()) * checkRT);

        if (checkRT > params.getMax()) {
            checkRT = params.getMax();
        }

        if (checkRT < params.getMin()) {
            checkRT = params.getMin();
        }

        task.setCheckRTNormalized(checkRT);
    }

    protected void checkOK(IpAddress ip, HealthCheckTask task, String msg) {
        Cluster cluster = task.getCluster();

        try {
            if (!ip.isValid() || !ip.isMockValid()) {
                if (ip.getOKCount().incrementAndGet() >= Switch.getCheckTimes()) {
                    if (cluster.responsible(ip)) {
                        ip.setValid(true);
                        ip.setMockValid(true);

                        VirtualClusterDomain vDom = (VirtualClusterDomain) cluster.getDom();
                        vDom.setLastModifiedMillis(System.currentTimeMillis());

                        PushService.domChanged(vDom.getNamespaceId(), vDom.getName());
                        addResult(new HealthCheckResult(vDom.getName(), ip));

                        Loggers.EVT_LOG.info("dom: {} {POS} {IP-ENABLED} valid: {}:{}@{}, region: {}, msg: {}",
                            cluster.getDom().getName(), ip.getIp(), ip.getPort(), cluster.getName(), DistroMapper.LOCALHOST_SITE, msg);
                    } else {
                        if (!ip.isMockValid()) {
                            ip.setMockValid(true);
                            Loggers.EVT_LOG.info("dom: {} {PROBE} {IP-ENABLED} valid: {}:{}@{}, region: {}, msg: {}",
                                cluster.getDom().getName(), ip.getIp(), ip.getPort(), cluster.getName(), DistroMapper.LOCALHOST_SITE, msg);
                        }
                    }
                } else {
                    Loggers.EVT_LOG.info("dom: {} {OTHER} {IP-ENABLED} pre-valid: {}:{}@{} in {}, msg: {}",
                        cluster.getDom().getName(), ip.getIp(), ip.getPort(), cluster.getName(), ip.getOKCount(), msg);
                }
            }
        } catch (Throwable t) {
            Loggers.SRV_LOG.error("[CHECK-OK] error when close check task.", t);
        }

        ip.getFailCount().set(0);
        ip.setBeingChecked(false);
    }

    protected void checkFail(IpAddress ip, HealthCheckTask task, String msg) {
        Cluster cluster = task.getCluster();

        try {
            if (ip.isValid() || ip.isMockValid()) {
                if (ip.getFailCount().incrementAndGet() >= Switch.getCheckTimes()) {
                    if (cluster.responsible(ip)) {
                        ip.setValid(false);
                        ip.setMockValid(false);

                        VirtualClusterDomain vDom = (VirtualClusterDomain) cluster.getDom();
                        vDom.setLastModifiedMillis(System.currentTimeMillis());
                        addResult(new HealthCheckResult(vDom.getName(), ip));

                        PushService.domChanged(vDom.getNamespaceId(), vDom.getName());

                        Loggers.EVT_LOG.info("dom: {} {POS} {IP-DISABLED} invalid: {}:{}@{}, region: {}, msg: {}",
                            cluster.getDom().getName(), ip.getIp(), ip.getPort(), cluster.getName(), DistroMapper.LOCALHOST_SITE, msg);
                    } else {
                        Loggers.EVT_LOG.info("dom: {} {PROBE} {IP-DISABLED} invalid: {}:{}@{}, region: {}, msg: {}",
                            cluster.getDom().getName(), ip.getIp(), ip.getPort(), cluster.getName(), DistroMapper.LOCALHOST_SITE, msg);
                    }

                } else {
                    Loggers.EVT_LOG.info("dom: {} {OTHER} {IP-DISABLED} pre-invalid: {}:{}@{} in {}, msg: {}",
                        cluster.getDom().getName(), ip.getIp(), ip.getPort(), cluster.getName(), ip.getFailCount(), msg);
                }
            }
        } catch (Throwable t) {
            Loggers.SRV_LOG.error("[CHECK-FAIL] error when close check task.", t);
        }

        ip.getOKCount().set(0);

        ip.setBeingChecked(false);
    }

    protected void checkFailNow(IpAddress ip, HealthCheckTask task, String msg) {
        Cluster cluster = task.getCluster();
        try {
            if (ip.isValid() || ip.isMockValid()) {
                if (cluster.responsible(ip)) {
                    ip.setValid(false);
                    ip.setMockValid(false);

                    VirtualClusterDomain vDom = (VirtualClusterDomain) cluster.getDom();
                    vDom.setLastModifiedMillis(System.currentTimeMillis());

                    PushService.domChanged(vDom.getNamespaceId(), vDom.getName());
                    addResult(new HealthCheckResult(vDom.getName(), ip));

                    Loggers.EVT_LOG.info("dom: {} {POS} {IP-DISABLED} invalid-now: {}:{}@{}, region: {}, msg: {}",
                        cluster.getDom().getName(), ip.getIp(), ip.getPort(), cluster.getName(), DistroMapper.LOCALHOST_SITE, msg);
                } else {
                    if (ip.isMockValid()) {
                        ip.setMockValid(false);
                        Loggers.EVT_LOG.info("dom: {} {PROBE} {IP-DISABLED} invalid-now: {}:{}@{}, region: {}, msg: {}",
                            cluster.getDom().getName(), ip.getIp(), ip.getPort(), cluster.getName(), DistroMapper.LOCALHOST_SITE, msg);
                    }

                }
            }
        } catch (Throwable t) {
            Loggers.SRV_LOG.error("[CHECK-FAIL-NOW] error when close check task.", t);
        }

        ip.getOKCount().set(0);
        ip.setBeingChecked(false);
    }
}
