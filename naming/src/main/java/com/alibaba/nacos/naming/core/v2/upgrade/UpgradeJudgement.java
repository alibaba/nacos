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

import com.alibaba.nacos.api.exception.NacosException;
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
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.RefreshStorageDataTask;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute.AsyncServicesCheckTask;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.util.VersionUtil;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
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
    
    private final AtomicBoolean all20XVersion = new AtomicBoolean(false);
    
    private final RaftPeerSet raftPeerSet;
    
    private final RaftCore raftCore;
    
    private final ClusterVersionJudgement versionJudgement;
    
    private final ServerMemberManager memberManager;
    
    private final ServiceManager serviceManager;
    
    private final DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine;
    
    private ScheduledExecutorService upgradeChecker;
    
    private static final int MAJOR_VERSION = 2;
    
    private static final int MINOR_VERSION = 4;
    
    public UpgradeJudgement(RaftPeerSet raftPeerSet, RaftCore raftCore, ClusterVersionJudgement versionJudgement,
            ServerMemberManager memberManager, ServiceManager serviceManager,
            UpgradeStates upgradeStates,
            DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine) {
        this.raftPeerSet = raftPeerSet;
        this.raftCore = raftCore;
        this.versionJudgement = versionJudgement;
        this.memberManager = memberManager;
        this.serviceManager = serviceManager;
        this.doubleWriteDelayTaskEngine = doubleWriteDelayTaskEngine;
        Boolean upgraded = upgradeStates.isUpgraded();
        upgraded = upgraded != null && upgraded;
        boolean isStandaloneMode = EnvUtil.getStandaloneMode();
        if (isStandaloneMode || upgraded) {
            useGrpcFeatures.set(true);
            useJraftFeatures.set(true);
            all20XVersion.set(true);
        }
        if (!isStandaloneMode) {
            initUpgradeChecker();
        }
        NotifyCenter.registerSubscriber(this);
    }
    
    private void initUpgradeChecker() {
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
    
    public boolean isAll20XVersion() {
        return all20XVersion.get();
    }
    
    @Override
    public void onEvent(MembersChangeEvent event) {
        if (!event.hasTriggers()) {
            Loggers.SRV_LOG.info("Member change without no trigger. "
                    + "It may be triggered by member lookup on startup. "
                    + "Skip.");
            return;
        }
        Loggers.SRV_LOG.info("member change, event: {}", event);
        for (Member each : event.getTriggers()) {
            Object versionStr = each.getExtendVal(MemberMetaDataConstants.VERSION);
            // come from below 1.3.0
            if (null == versionStr) {
                checkAndDowngrade(false);
                all20XVersion.set(false);
                return;
            }
            Version version = VersionUtil.parseVersion(versionStr.toString());
            if (version.getMajorVersion() < MAJOR_VERSION) {
                checkAndDowngrade(version.getMinorVersion() >= MINOR_VERSION);
                all20XVersion.set(false);
                return;
            }
        }
        all20XVersion.set(true);
    }
    
    private void checkAndDowngrade(boolean jraftFeature) {
        boolean isDowngradeGrpc = useGrpcFeatures.compareAndSet(true, false);
        boolean isDowngradeJraft = useJraftFeatures.getAndSet(jraftFeature);
        if (isDowngradeGrpc && isDowngradeJraft && !jraftFeature) {
            Loggers.SRV_LOG.info("Downgrade to 1.X");
            NotifyCenter.publishEvent(new UpgradeStates.UpgradeStateChangedEvent(false));
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
            if (!selfCheckResult) {
                NamingExecuteTaskDispatcher.getInstance().dispatchAndExecuteTask(AsyncServicesCheckTask.class,
                        new AsyncServicesCheckTask(doubleWriteDelayTaskEngine, this));
            }
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
        NotifyCenter.publishEvent(new UpgradeStates.UpgradeStateChangedEvent(true));
        useJraftFeatures.set(true);
        refreshPersistentServices();
    }
    
    private void refreshPersistentServices() {
        for (String each : com.alibaba.nacos.naming.core.v2.ServiceManager.getInstance().getAllNamespaces()) {
            for (Service service : com.alibaba.nacos.naming.core.v2.ServiceManager.getInstance().getSingletons(each)) {
                NamingExecuteTaskDispatcher.getInstance()
                        .dispatchAndExecuteTask(service, new RefreshStorageDataTask(service));
            }
        }
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return MembersChangeEvent.class;
    }
    
    /**
     * Shut down.
     */
    @PreDestroy
    public void shutdown() {
        if (null != upgradeChecker) {
            upgradeChecker.shutdownNow();
        }
    }
    
    /**
     * Stop judgement and clear all cache.
     */
    public void stopAll() {
        try {
            Loggers.SRV_LOG.info("Disable Double write, stop and clean v1.x cache and features");
            useGrpcFeatures.set(true);
            NotifyCenter.publishEvent(new UpgradeStates.UpgradeStateChangedEvent(true));
            useJraftFeatures.set(true);
            NotifyCenter.deregisterSubscriber(this);
            doubleWriteDelayTaskEngine.shutdown();
            if (null != upgradeChecker) {
                upgradeChecker.shutdownNow();
            }
            serviceManager.shutdown();
            raftCore.shutdown();
        } catch (NacosException e) {
            Loggers.SRV_LOG.info("Close double write with exception", e);
        }
    }
}
