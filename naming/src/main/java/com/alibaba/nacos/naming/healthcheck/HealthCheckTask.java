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

import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.Switch;
import org.apache.commons.lang3.RandomUtils;

/**
 * @author nacos
 */
public class HealthCheckTask implements Runnable {

    private Cluster cluster;

    private long checkRTNormalized = -1;
    private long checkRTBest = -1;
    private long checkRTWorst = -1;

    private long checkRTLast = -1;
    private long checkRTLastLast = -1;

    private long startTime;

    private volatile boolean cancelled = false;

    public HealthCheckTask(Cluster cluster) {
        this.cluster = cluster;
        initCheckRT();
    }

    public void initCheckRT() {
        // first check time delay
        checkRTNormalized = 2000 + RandomUtils.nextInt(0, Switch.getTcpHealthParams().getMax());

        checkRTBest = Long.MAX_VALUE;
        checkRTWorst = 0L;
    }

    @Override
    public void run() {
        AbstractHealthCheckProcessor processor = AbstractHealthCheckProcessor.getProcessor(cluster.getHealthChecker());

        try {
            if (DistroMapper.responsible(cluster.getDom().getName())) {
                processor.process(this);
                Loggers.EVT_LOG.debug("[HEALTH-CHECK] schedule health check task: {}", cluster.getDom().getName());
            }
        } catch (Throwable e) {
            Loggers.SRV_LOG.error("[HEALTH-CHECK] error while process health check for {}:{}, error: {}",
                cluster.getDom().getName(), cluster.getName(), e);
        } finally {
            if (!cancelled) {
                HealthCheckReactor.scheduleCheck(this);

                // worst == 0 means never checked
                if (this.getCheckRTWorst() > 0
                    && Switch.isHealthCheckEnabled(cluster.getDom().getName())
                    && DistroMapper.responsible(cluster.getDom().getName())) {
                    // TLog doesn't support float so we must convert it into long
                    long diff = ((this.getCheckRTLast() - this.getCheckRTLastLast()) * 10000)
                        / this.getCheckRTLastLast();

                    this.setCheckRTLastLast(this.getCheckRTLast());

                    Cluster cluster = this.getCluster();
                    if (((VirtualClusterDomain) cluster.getDom()).getEnableHealthCheck()) {
                        Loggers.CHECK_RT.info("{}:{}@{}->normalized: {}, worst: {}, best: {}, last: {}, diff: {}",
                            cluster.getDom().getName(), cluster.getName(), processor.getType(),
                            this.getCheckRTNormalized(), this.getCheckRTWorst(), this.getCheckRTBest(),
                            this.getCheckRTLast(), diff);
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

    public long getCheckRTNormalized() {
        return checkRTNormalized;
    }

    public long getCheckRTBest() {
        return checkRTBest;
    }

    public long getCheckRTWorst() {
        return checkRTWorst;
    }

    public void setCheckRTWorst(long checkRTWorst) {
        this.checkRTWorst = checkRTWorst;
    }

    public void setCheckRTBest(long checkRTBest) {
        this.checkRTBest = checkRTBest;
    }

    public void setCheckRTNormalized(long checkRTNormalized) {
        this.checkRTNormalized = checkRTNormalized;
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

    public long getCheckRTLast() {
        return checkRTLast;
    }

    public void setCheckRTLast(long checkRTLast) {
        this.checkRTLast = checkRTLast;
    }

    public long getCheckRTLastLast() {
        return checkRTLastLast;
    }

    public void setCheckRTLastLast(long checkRTLastLast) {
        this.checkRTLastLast = checkRTLastLast;
    }
}
