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
package com.alibaba.nacos.config.server.model.app;

import static com.alibaba.nacos.core.utils.SystemUtils.LOCAL_IP;

/**
 * app info
 *
 * @author Nacos
 */
public class ApplicationInfo {

    private static final long LOCK_EXPIRE_DURATION = 30 * 1000;
    private static final long RECENTLY_DURATION = 24 * 60 * 60 * 1000;

    private String appName;

    private boolean isDynamicCollectDisabled = false;

    private long lastSubscribeInfoCollectedTime = 0L;

    private String subInfoCollectLockOwner = null;

    private long subInfoCollectLockExpireTime = 0L;

    public ApplicationInfo(String appName) {
        this.appName = appName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public boolean isDynamicCollectDisabled() {
        return isDynamicCollectDisabled;
    }

    public void setDynamicCollectDisabled(boolean isDynamicCollectDisabled) {
        this.isDynamicCollectDisabled = isDynamicCollectDisabled;
    }

    public long getLastSubscribeInfoCollectedTime() {
        return lastSubscribeInfoCollectedTime;
    }

    public void setLastSubscribeInfoCollectedTime(
        long lastSubscribeInfoCollectedTime) {
        this.lastSubscribeInfoCollectedTime = lastSubscribeInfoCollectedTime;
    }

    public String getSubInfoCollectLockOwner() {
        return subInfoCollectLockOwner;
    }

    public void setSubInfoCollectLockOwner(String subInfoCollectLockOwner) {
        this.subInfoCollectLockOwner = subInfoCollectLockOwner;
    }

    public long getSubInfoCollectLockExpireTime() {
        return subInfoCollectLockExpireTime;
    }

    public void setSubInfoCollectLockExpireTime(
        long subInfoCollectLockExpireTime) {
        this.subInfoCollectLockExpireTime = subInfoCollectLockExpireTime;
    }

    public boolean isSubInfoRecentlyCollected() {
        if (System.currentTimeMillis() - this.lastSubscribeInfoCollectedTime < RECENTLY_DURATION) {
            return true;
        }
        return false;
    }

    public boolean canCurrentServerOwnTheLock() {
        boolean currentOwnerIsMe = subInfoCollectLockOwner == null || LOCAL_IP
            .equals(subInfoCollectLockOwner);

        if (currentOwnerIsMe) {
            return true;
        }
        if (System.currentTimeMillis() - this.subInfoCollectLockExpireTime > LOCK_EXPIRE_DURATION) {
            return true;
        }

        return false;
    }

    public String currentServer() {
        return LOCAL_IP;
    }

}
