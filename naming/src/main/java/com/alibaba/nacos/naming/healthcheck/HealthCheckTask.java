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

import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang3.RandomUtils;

/**
 * Health check task.
 *
 * @author nacos
 */
public class HealthCheckTask implements Runnable {
    
    private Cluster cluster;
    
    private long checkRtNormalized = -1;
    
    private long checkRtBest = -1;
    
    private long checkRtWorst = -1;
    
    private long checkRtLast = -1;
    
    private long checkRtLastLast = -1;
    
    private long startTime;
    
    private volatile boolean cancelled = false;
    
    @JsonIgnore
    private final DistroMapper distroMapper;
    
    @JsonIgnore
    private final SwitchDomain switchDomain;
    
    @JsonIgnore
    private final HealthCheckProcessor healthCheckProcessor;
    
    public HealthCheckTask(Cluster cluster) {
        this.cluster = cluster;
        distroMapper = ApplicationUtils.getBean(DistroMapper.class);
        switchDomain = ApplicationUtils.getBean(SwitchDomain.class);
        healthCheckProcessor = ApplicationUtils.getBean(HealthCheckProcessorDelegate.class);
        initCheckRT();
    }
    
    private void initCheckRT() {
        // first check time delay
        checkRtNormalized =
                2000 + RandomUtils.nextInt(0, RandomUtils.nextInt(0, switchDomain.getTcpHealthParams().getMax()));
        checkRtBest = Long.MAX_VALUE;
        checkRtWorst = 0L;
    }
    
    @Override
    public void run() {
        
        try {
            // If upgrade to 2.0.X stop health check with v1
            if (ApplicationUtils.getBean(UpgradeJudgement.class).isUseGrpcFeatures()) {
                return;
            }
            if (distroMapper.responsible(cluster.getService().getName()) && switchDomain
                    .isHealthCheckEnabled(cluster.getService().getName())) {
                healthCheckProcessor.process(this);
                if (Loggers.EVT_LOG.isDebugEnabled()) {
                    Loggers.EVT_LOG
                            .debug("[HEALTH-CHECK] schedule health check task: {}", cluster.getService().getName());
                }
            }
        } catch (Throwable e) {
            Loggers.SRV_LOG
                    .error("[HEALTH-CHECK] error while process health check for {}:{}", cluster.getService().getName(),
                            cluster.getName(), e);
        } finally {
            if (!cancelled) {
                HealthCheckReactor.scheduleCheck(this);
                
                // worst == 0 means never checked
                if (this.getCheckRtWorst() > 0 && switchDomain.isHealthCheckEnabled(cluster.getService().getName())
                        && distroMapper.responsible(cluster.getService().getName())) {
                    // TLog doesn't support float so we must convert it into long
                    long diff =
                            ((this.getCheckRtLast() - this.getCheckRtLastLast()) * 10000) / this.getCheckRtLastLast();
                    
                    this.setCheckRtLastLast(this.getCheckRtLast());
                    
                    Cluster cluster = this.getCluster();
                    
                    if (Loggers.CHECK_RT.isDebugEnabled()) {
                        Loggers.CHECK_RT.debug("{}:{}@{}->normalized: {}, worst: {}, best: {}, last: {}, diff: {}",
                                cluster.getService().getName(), cluster.getName(), cluster.getHealthChecker().getType(),
                                this.getCheckRtNormalized(), this.getCheckRtWorst(), this.getCheckRtBest(),
                                this.getCheckRtLast(), diff);
                    }
                }
            }
        }
    }
    
    public Cluster getCluster() {
        return cluster;
    }
    
    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }
    
    public long getCheckRtNormalized() {
        return checkRtNormalized;
    }
    
    public long getCheckRtBest() {
        return checkRtBest;
    }
    
    public long getCheckRtWorst() {
        return checkRtWorst;
    }
    
    public void setCheckRtWorst(long checkRtWorst) {
        this.checkRtWorst = checkRtWorst;
    }
    
    public void setCheckRtBest(long checkRtBest) {
        this.checkRtBest = checkRtBest;
    }
    
    public void setCheckRtNormalized(long checkRtNormalized) {
        this.checkRtNormalized = checkRtNormalized;
    }
    
    public boolean isCancelled() {
        return cancelled;
    }
    
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getCheckRtLast() {
        return checkRtLast;
    }
    
    public void setCheckRtLast(long checkRtLast) {
        this.checkRtLast = checkRtLast;
    }
    
    public long getCheckRtLastLast() {
        return checkRtLastLast;
    }
    
    public void setCheckRtLastLast(long checkRtLastLast) {
        this.checkRtLastLast = checkRtLastLast;
    }
}
