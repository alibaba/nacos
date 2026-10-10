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

package com.alibaba.nacos.client.config.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.LogUtils;
import org.slf4j.Logger;

/**
 * Snapshot switch.
 *
 * @author Nacos
 */
public class SnapShotSwitch {
    
    private static final Logger LOGGER = LogUtils.logger(SnapShotSwitch.class);
    
    /**
     * whether use local cache.
     */
    private static Boolean isSnapShot;
    
    static {
        initSnapshotSwitch();
    }
    
    public static Boolean getIsSnapShot() {
        return isSnapShot;
    }
    
    public static void setIsSnapShot(Boolean isSnapShot) {
        SnapShotSwitch.isSnapShot = isSnapShot;
        LocalConfigInfoProcessor.cleanAllSnapshot();
    }
    
    static void initSnapshotSwitch() {
        isSnapShot = NacosClientProperties.PROTOTYPE
            .getBoolean(PropertyKeyConst.CONFIG_SNAPSHOT_ENABLED, true);
        if (!isSnapShot) {
            cleanSnapshotOnStartup();
        }
    }
    
    /**
     * Best-effort snapshot cleanup executed while snapshots are being disabled.
     *
     * <p>This runs from the static initializer, so a recoverable cleanup failure must not escape as
     * an {@code ExceptionInInitializerError} that breaks every later snapshot access. Fatal VM
     * errors are intentionally not caught.
     */
    private static void cleanSnapshotOnStartup() {
        try {
            LocalConfigInfoProcessor.cleanAllSnapshot();
        } catch (LinkageError | RuntimeException e) {
            LOGGER.warn("Failed to clean snapshot on startup, config snapshot stays disabled.", e);
        }
    }
    
}
