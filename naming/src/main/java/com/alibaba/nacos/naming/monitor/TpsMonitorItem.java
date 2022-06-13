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

/**
 * Naming Tps monitor items.
 *
 * @author xiweng.yy
 */
public enum TpsMonitorItem {
    
    /**
     * Naming rpc total push.
     */
    NAMING_RPC_PUSH,
    
    /**
     * Naming rpc success push.
     */
    NAMING_RPC_PUSH_SUCCESS,
    
    /**
     * Naming rpc failed push.
     */
    NAMING_RPC_PUSH_FAIL,
    
    /**
     * Naming udp total push.
     */
    NAMING_UDP_PUSH,
    
    /**
     * Naming udp success push.
     */
    NAMING_UDP_PUSH_SUCCESS,
    
    /**
     * Naming udp fail push.
     */
    NAMING_UDP_PUSH_FAIL,
    
    /**
     * Naming rpc distro sync total count.
     */
    NAMING_DISTRO_SYNC,
    
    /**
     * Naming rpc distro sync success count.
     */
    NAMING_DISTRO_SYNC_SUCCESS,
    
    /**
     * Naming rpc distro sync fail count.
     */
    NAMING_DISTRO_SYNC_FAIL,
    
    /**
     * Naming rpc distro verify fail count.
     */
    NAMING_DISTRO_VERIFY,
    
    /**
     * Naming rpc distro verify fail count.
     */
    NAMING_DISTRO_VERIFY_SUCCESS,
    
    /**
     * Naming rpc distro verify fail count.
     */
    NAMING_DISTRO_VERIFY_FAIL,
}
