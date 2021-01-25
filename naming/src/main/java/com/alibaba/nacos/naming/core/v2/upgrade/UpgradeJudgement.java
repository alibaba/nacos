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

package com.alibaba.nacos.naming.core.v2.upgrade;

import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.consistency.persistent.ClusterVersionJudgement;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeerSet;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.util.VersionUtil;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ability judgement during upgrading.
 *
 * @author xiweng.yy
 */
@Component
public class UpgradeJudgement extends Subscriber<MembersChangeEvent> {
    
    /**
     * Only when all cluster upgrade upper 2.0.0, this features is true.
     */
    private final AtomicBoolean useGrpcFeatures = new AtomicBoolean(false);
    
    /**
     * Only when all cluster upgrade upper 1.4.0, this features is true.
     */
    private final AtomicBoolean useJraftFeatures = new AtomicBoolean(false);
    
    private final RaftPeerSet raftPeerSet;
    
    private final RaftCore raftCore;
    
    private final ClusterVersionJudgement versionJudgement;
    
    private final ServerMemberManager memberManager;
    
    private final ServiceManager serviceManager;
    
    private final DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine;
    
    private final ScheduledExecutorService upgradeChecker;
    
    public UpgradeJudgement(RaftPeerSet raftPeerSet, RaftCore raftCore, ClusterVersionJudgement versionJudgement,
            ServerMemberManager memberManager, ServiceManager serviceManager,
            DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine) {
        this.raftPeerSet = raftPeerSet;
        this.raftCore = raftCore;
        this.versionJudgement = versionJudgement;
        this.memberManager = memberManager;
        this.serviceManager = serviceManager;
        this.doubleWriteDelayTaskEngine = doubleWriteDelayTaskEngine;
        upgradeChecker = ExecutorFactory.newSingleScheduledExecutorService(new NameThreadFactory("upgrading.checker"));
        upgradeChecker.scheduleAtFixedRate(() -> {
            if (isUseGrpcFeatures()) {
                return;
            }
            boolean canUpgrade = checkForUpgrade();
            Loggers.SRV_LOG.info("upgrade check result {}", canUpgrade);
            if (canUpgrade) {
                doUpgrade();
            }
        }, 100L, 5000L, TimeUnit.MILLISECONDS);
        NotifyCenter.registerSubscriber(this);
    }
    
    @JustForTest
    void setUseGrpcFeatures(boolean value) {
        useGrpcFeatures.set(value);
    }
    
    @JustForTest
    void setUseJraftFeatures(boolean value) {
        useJraftFeatures.set(value);
    }
    
    public boolean isUseGrpcFeatures() {
        return useGrpcFeatures.get();
    }
    
    public boolean isUseJraftFeatures() {
        return useJraftFeatures.get();
    }
    
    @Override
    public void onEvent(MembersChangeEvent event) {
        Loggers.SRV_LOG.info("member change, new members {}", event.getMembers());
        for (Member each : event.getMembers()) {
            Object versionStr = each.getExtendVal(MemberMetaDataConstants.VERSION);
            // come from below 1.3.0
            if (null == versionStr) {
                checkAndDowngrade(false);
                return;
            }
            Version version = VersionUtil.parseVersion(versionStr.toString());
            if (version.getMajorVersion() < 2) {
                checkAndDowngrade(version.getMinorVersion() >= 4);
                return;
            }
        }
    }
    
    private void checkAndDowngrade(boolean jraftFeature) {
        boolean isDowngradeGrpc = useGrpcFeatures.compareAndSet(true, false);
        boolean isDowngradeJraft = useJraftFeatures.getAndSet(jraftFeature);
        if (isDowngradeGrpc && isDowngradeJraft && !jraftFeature) {
            Loggers.SRV_LOG.info("Downgrade to 1.X");
            try {
                raftPeerSet.init();
                raftCore.init();
                versionJudgement.reset();
            } catch (Exception e) {
                Loggers.SRV_LOG.error("Downgrade rafe failed ", e);
            }
        }
    }
    
    private boolean checkForUpgrade() {
        if (!useGrpcFeatures.get()) {
            boolean selfCheckResult = checkServiceAndInstanceNumber() && checkDoubleWriteStatus();
            Member self = memberManager.getSelf();
            self.setExtendVal(MemberMetaDataConstants.READY_TO_UPGRADE, selfCheckResult);
            memberManager.updateMember(self);
        }
        boolean result = true;
        for (Member each : memberManager.allMembers()) {
            Object isReadyToUpgrade = each.getExtendVal(MemberMetaDataConstants.READY_TO_UPGRADE);
            result &= null != isReadyToUpgrade && (boolean) isReadyToUpgrade;
        }
        return result;
    }
    
    private boolean checkServiceAndInstanceNumber() {
        boolean result = serviceManager.getServiceCount() == MetricsMonitor.getDomCountMonitor().get();
        result &= serviceManager.getInstanceCount() == MetricsMonitor.getIpCountMonitor().get();
        return result;
    }
    
    private boolean checkDoubleWriteStatus() {
        return doubleWriteDelayTaskEngine.isEmpty();
    }
    
    private void doUpgrade() {
        Loggers.SRV_LOG.info("Upgrade to 2.0.X");
        useGrpcFeatures.compareAndSet(false, true);
        useJraftFeatures.set(true);
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return MembersChangeEvent.class;
    }
    
    public void shutdown() {
        upgradeChecker.shutdownNow();
    }
}
