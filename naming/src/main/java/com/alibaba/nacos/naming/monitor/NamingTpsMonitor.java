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

package com.alibaba.nacos.naming.monitor;

import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.context.ControlContext;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;

/**
 * TPS monitor and push admission singleton for Naming.
 *
 * @author xiweng.yy
 */
public class NamingTpsMonitor {
    
    private static final NamingTpsMonitor INSTANCE = new NamingTpsMonitor();
    
    private final TpsControlManager tpsControlManager =
        ControlManagerCenter.getInstance().getTpsControlManager();
    
    private NamingTpsMonitor() {
        registerPushMonitorPoint();
        registerDistroMonitorPoint();
    }
    
    private void registerPushMonitorPoint() {
        tpsControlManager.registerTpsPoint(TpsMonitorItem.NAMING_RPC_PUSH.name());
        tpsControlManager.registerTpsPoint(TpsMonitorItem.NAMING_RPC_PUSH_SUCCESS.name());
        tpsControlManager.registerTpsPoint(TpsMonitorItem.NAMING_RPC_PUSH_FAIL.name());
        tpsControlManager.registerTpsPoint(TpsMonitorItem.NAMING_PUSH_TASK.name());
    }
    
    private void registerDistroMonitorPoint() {
        tpsControlManager.registerTpsPoint(TpsMonitorItem.NAMING_DISTRO_SYNC.name());
        tpsControlManager.registerTpsPoint(TpsMonitorItem.NAMING_DISTRO_SYNC_SUCCESS.name());
        tpsControlManager.registerTpsPoint(TpsMonitorItem.NAMING_DISTRO_SYNC_FAIL.name());
        tpsControlManager.registerTpsPoint(TpsMonitorItem.NAMING_DISTRO_VERIFY.name());
        tpsControlManager.registerTpsPoint(TpsMonitorItem.NAMING_DISTRO_VERIFY_SUCCESS.name());
        tpsControlManager.registerTpsPoint(TpsMonitorItem.NAMING_DISTRO_VERIFY_FAIL.name());
    }
    
    public static NamingTpsMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Check whether a naming push task may be dispatched now.
     *
     * <p>The selected control plugin receives the current push backlog and the target subscriber
     * count in the check context, so it can apply absolute watermarks instead of only a
     * per-second rate. A plugin failure is treated as an allow decision to keep push semantics
     * unchanged.
     *
     * @param service service of the push task
     * @param pendingPushTasks number of push tasks waiting in the delay engine
     * @param targetSubscribers number of subscribers targeted by the push task
     * @return {@code true} when the push task may be dispatched
     */
    public static boolean isPushTaskAllowed(Service service, int pendingPushTasks,
        int targetSubscribers) {
        return isPushTaskAllowed(INSTANCE.tpsControlManager, service, pendingPushTasks,
            targetSubscribers);
    }
    
    static boolean isPushTaskAllowed(TpsControlManager tpsControlManager, Service service,
        int pendingPushTasks, int targetSubscribers) {
        TpsCheckRequest request =
            new TpsCheckRequest(TpsMonitorItem.NAMING_PUSH_TASK.name(), null, null);
        request.setControlContext(ControlContext.builder()
            .metric(ControlContext.PUSH_PENDING_TASKS, pendingPushTasks)
            .metric(ControlContext.PUSH_TARGET_SUBSCRIBERS, targetSubscribers)
            .attribute(ControlContext.PUSH_SERVICE_NAMESPACE, service.getNamespace())
            .attribute(ControlContext.PUSH_SERVICE_GROUP, service.getGroup())
            .attribute(ControlContext.PUSH_SERVICE_NAME, service.getName())
            .build());
        try {
            TpsCheckResponse response = tpsControlManager.check(request);
            return response.isSuccess();
        } catch (Throwable throwable) {
            Loggers.PUSH.error("[PUSH] Control plugin check failed for point "
                + TpsMonitorItem.NAMING_PUSH_TASK.name() + ", pass by default.", throwable);
            return true;
        }
    }
    
    /**
     * Apply RPC push success.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void rpcPushSuccess(String clientId, String clientIp) {
        INSTANCE.tpsControlManager
            .check(new TpsCheckRequest(TpsMonitorItem.NAMING_RPC_PUSH.name(), clientId, clientIp));
        INSTANCE.tpsControlManager.check(
            new TpsCheckRequest(TpsMonitorItem.NAMING_RPC_PUSH_SUCCESS.name(), clientId, clientIp));
    }
    
    /**
     * Apply RPC push fail.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void rpcPushFail(String clientId, String clientIp) {
        INSTANCE.tpsControlManager
            .check(new TpsCheckRequest(TpsMonitorItem.NAMING_RPC_PUSH.name(), clientId, clientIp));
        INSTANCE.tpsControlManager.check(
            new TpsCheckRequest(TpsMonitorItem.NAMING_RPC_PUSH_FAIL.name(), clientId, clientIp));
    }
    
    /**
     * Apply distro sync success.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void distroSyncSuccess(String clientId, String clientIp) {
        INSTANCE.tpsControlManager.check(
            new TpsCheckRequest(TpsMonitorItem.NAMING_DISTRO_SYNC.name(), clientId, clientIp));
        INSTANCE.tpsControlManager
            .check(new TpsCheckRequest(TpsMonitorItem.NAMING_DISTRO_SYNC_SUCCESS.name(), clientId,
                clientIp));
    }
    
    /**
     * Apply distro sync fail.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void distroSyncFail(String clientId, String clientIp) {
        INSTANCE.tpsControlManager.check(
            new TpsCheckRequest(TpsMonitorItem.NAMING_DISTRO_SYNC.name(), clientId, clientIp));
        INSTANCE.tpsControlManager
            .check(new TpsCheckRequest(TpsMonitorItem.NAMING_DISTRO_SYNC_FAIL.name(), clientId,
                clientIp));
    }
    
    /**
     * Apply distro verify success.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void distroVerifySuccess(String clientId, String clientIp) {
        INSTANCE.tpsControlManager.check(
            new TpsCheckRequest(TpsMonitorItem.NAMING_DISTRO_VERIFY.name(), clientId, clientIp));
        INSTANCE.tpsControlManager
            .check(new TpsCheckRequest(TpsMonitorItem.NAMING_DISTRO_VERIFY_SUCCESS.name(), clientId,
                clientIp));
    }
    
    /**
     * Apply distro verify fail.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void distroVerifyFail(String clientId, String clientIp) {
        INSTANCE.tpsControlManager.check(
            new TpsCheckRequest(TpsMonitorItem.NAMING_DISTRO_VERIFY.name(), clientId, clientIp));
        INSTANCE.tpsControlManager
            .check(new TpsCheckRequest(TpsMonitorItem.NAMING_DISTRO_VERIFY_FAIL.name(), clientId,
                clientIp));
    }
    
}
