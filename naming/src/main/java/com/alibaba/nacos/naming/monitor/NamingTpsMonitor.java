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

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreaker;
import com.alibaba.nacos.core.remote.control.*;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Tps and flow control monitor singleton for Naming.
 *
 * @author xiweng.yy
 */
public class NamingTpsMonitor {
    
    private static final NamingTpsMonitor INSTANCE = new NamingTpsMonitor();
    
//    private final TpsMonitorManager tpsMonitorManager;
    private final CircuitBreaker circuitBreaker;
    
    private NamingTpsMonitor() {
//        this.tpsMonitorManager = ApplicationUtils.getBean(TpsMonitorManager.class);
        this.circuitBreaker = ApplicationUtils.getBean(CircuitBreaker.class);
        registerPushMonitorPoint();
        registerDistroMonitorPoint();
    }
    
    private void registerPushMonitorPoint() {
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_RPC_PUSH.name());
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_RPC_PUSH_SUCCESS.name());
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_RPC_PUSH_FAIL.name());
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_UDP_PUSH.name());
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_UDP_PUSH_SUCCESS.name());
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_UDP_PUSH_FAIL.name());
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_RPC_PUSH.name()));
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_RPC_PUSH_SUCCESS.name()));
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_RPC_PUSH_FAIL.name()));
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_UDP_PUSH.name()));
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_UDP_PUSH_SUCCESS.name()));
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_UDP_PUSH_FAIL.name()));
    }
    
    private void registerDistroMonitorPoint() {
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_DISTRO_SYNC.name());
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_DISTRO_SYNC_SUCCESS.name());
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_DISTRO_SYNC_FAIL.name());
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_DISTRO_VERIFY.name());
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_DISTRO_VERIFY_SUCCESS.name());
        circuitBreaker.registerPoint(TpsMonitorItem.NAMING_DISTRO_VERIFY_FAIL.name());
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_DISTRO_SYNC.name()));
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_DISTRO_SYNC_SUCCESS.name()));
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_DISTRO_SYNC_FAIL.name()));
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_DISTRO_VERIFY.name()));
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_DISTRO_VERIFY_SUCCESS.name()));
//        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(TpsMonitorItem.NAMING_DISTRO_VERIFY_FAIL.name()));
    }
    
    public static NamingTpsMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Apply RPC push success.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void rpcPushSuccess(String clientId, String clientIp) {

        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new ConnectionIdMonitorKey(clientId));
        monitorKeyList.add(new ClientIpMonitorKey(clientIp));
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_RPC_PUSH.name(), monitorKeyList);
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_RPC_PUSH_SUCCESS.name(), monitorKeyList);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_RPC_PUSH.name(), clientId, clientIp);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_RPC_PUSH_SUCCESS.name(), clientId, clientIp);
    }
    
    /**
     * Apply RPC push fail.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void rpcPushFail(String clientId, String clientIp) {
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new ConnectionIdMonitorKey(clientId));
        monitorKeyList.add(new ClientIpMonitorKey(clientIp));
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_RPC_PUSH.name(), monitorKeyList);
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_RPC_PUSH_FAIL.name(), monitorKeyList);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_RPC_PUSH.name(), clientId, clientIp);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_RPC_PUSH_FAIL.name(), clientId, clientIp);
    }
    
    /**
     * Apply UDP push success.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void udpPushSuccess(String clientId, String clientIp) {
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new ConnectionIdMonitorKey(clientId));
        monitorKeyList.add(new ClientIpMonitorKey(clientIp));
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_UDP_PUSH.name(), monitorKeyList);
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_UDP_PUSH_SUCCESS.name(), monitorKeyList);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_UDP_PUSH.name(), clientId, clientIp);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_UDP_PUSH_SUCCESS.name(), clientId, clientIp);
    }
    
    /**
     * Apply UDP push fail.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void udpPushFail(String clientId, String clientIp) {
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new ConnectionIdMonitorKey(clientId));
        monitorKeyList.add(new ClientIpMonitorKey(clientIp));
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_UDP_PUSH.name(), monitorKeyList);
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_UDP_PUSH_FAIL.name(), monitorKeyList);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_UDP_PUSH.name(), clientId, clientIp);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_UDP_PUSH_FAIL.name(), clientId, clientIp);
    }
    
    /**
     * Apply distro sync success.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void distroSyncSuccess(String clientId, String clientIp) {
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new ConnectionIdMonitorKey(clientId));
        monitorKeyList.add(new ClientIpMonitorKey(clientIp));
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_DISTRO_SYNC.name(), monitorKeyList);
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_DISTRO_SYNC_SUCCESS.name(), monitorKeyList);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_DISTRO_SYNC.name(), clientId, clientIp);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_DISTRO_SYNC_SUCCESS.name(), clientId, clientIp);
    }
    
    /**
     * Apply distro sync fail.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void distroSyncFail(String clientId, String clientIp) {
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new ConnectionIdMonitorKey(clientId));
        monitorKeyList.add(new ClientIpMonitorKey(clientIp));
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_DISTRO_SYNC.name(), monitorKeyList);
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_DISTRO_SYNC_FAIL.name(), monitorKeyList);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_DISTRO_SYNC.name(), clientId, clientIp);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_DISTRO_SYNC_FAIL.name(), clientId, clientIp);
    }
    
    /**
     * Apply distro verify success.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void distroVerifySuccess(String clientId, String clientIp) {
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new ConnectionIdMonitorKey(clientId));
        monitorKeyList.add(new ClientIpMonitorKey(clientIp));
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_DISTRO_VERIFY.name(), monitorKeyList);
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_DISTRO_VERIFY_SUCCESS.name(), monitorKeyList);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_DISTRO_VERIFY.name(), clientId, clientIp);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_DISTRO_VERIFY_SUCCESS.name(), clientId, clientIp);
    }
    
    /**
     * Apply distro verify fail.
     *
     * @param clientId client id
     * @param clientIp client ip
     */
    public static void distroVerifyFail(String clientId, String clientIp) {
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new ConnectionIdMonitorKey(clientId));
        monitorKeyList.add(new ClientIpMonitorKey(clientIp));
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_DISTRO_VERIFY.name(), monitorKeyList);
        INSTANCE.circuitBreaker.applyStrategy(TpsMonitorItem.NAMING_DISTRO_VERIFY_FAIL.name(), monitorKeyList);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_DISTRO_VERIFY.name(), clientId, clientIp);
//        INSTANCE.tpsMonitorManager.applyTpsForClientIp(TpsMonitorItem.NAMING_DISTRO_VERIFY_FAIL.name(), clientId, clientIp);
    }
}
