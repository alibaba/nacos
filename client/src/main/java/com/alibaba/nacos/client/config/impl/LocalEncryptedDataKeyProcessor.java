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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.client.config.utils.ConcurrentDiskUtil;
import com.alibaba.nacos.client.config.utils.JvmUtil;
import com.alibaba.nacos.client.config.utils.SnapShotSwitch;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.utils.IoUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

/**
 * 密文数据密钥（EncryptedDataKey）的本地快照、容灾目录相关.
 *
 * @author luyanbo(RobberPhex)
 */
public class LocalEncryptedDataKeyProcessor extends LocalConfigInfoProcessor {
    
    private static final Logger LOGGER = LogUtils.logger(LocalEncryptedDataKeyProcessor.class);
    
    /**
     * 获取容灾配置的 EncryptedDataKey。NULL表示没有本地文件或抛出异常.
     */
    public static String getEncryptDataKeyFailover(String envName, String dataId, String group, String tenant) {
        File file = getEncryptDataKeyFailoverFile(envName, dataId, group, tenant);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        
        try {
            return readFile(file);
        } catch (IOException ioe) {
            LOGGER.error("[" + envName + "] get failover error, " + file, ioe);
            return null;
        }
    }
    
    /**
     * 获取本地缓存文件的 EncryptedDataKey。NULL表示没有本地文件或抛出异常.
     */
    public static String getEncryptDataKeySnapshot(String envName, String dataId, String group, String tenant) {
        if (!SnapShotSwitch.getIsSnapShot()) {
            return null;
        }
        File file = getEncryptDataKeySnapshotFile(envName, dataId, group, tenant);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        
        try {
            return readFile(file);
        } catch (IOException ioe) {
            LOGGER.error("[" + envName + "] get snapshot error, " + file, ioe);
            return null;
        }
    }
    
    /**
     * 保存 encryptDataKey 的snapshot。如果内容为NULL，则删除snapshot.
     */
    public static void saveEncryptDataKeySnapshot(String envName, String dataId, String group, String tenant,
            String encryptDataKey) {
        if (!SnapShotSwitch.getIsSnapShot()) {
            return;
        }
        File file = getEncryptDataKeySnapshotFile(envName, dataId, group, tenant);
        try {
            if (null == encryptDataKey) {
                try {
                    IoUtils.delete(file);
                } catch (IOException ioe) {
                    LOGGER.error("[" + envName + "] delete snapshot error, " + file, ioe);
                }
            } else {
                File parentFile = file.getParentFile();
                if (!parentFile.exists()) {
                    boolean isMdOk = parentFile.mkdirs();
                    if (!isMdOk) {
                        LOGGER.error("[{}] save snapshot error", envName);
                    }
                }
                if (JvmUtil.isMultiInstance()) {
                    ConcurrentDiskUtil.writeFileContent(file, encryptDataKey, Constants.ENCODE);
                } else {
                    IoUtils.writeStringToFile(file, encryptDataKey, Constants.ENCODE);
                }
            }
        } catch (IOException ioe) {
            LOGGER.error("[" + envName + "] save snapshot error, " + file, ioe);
        }
    }
    
    private static File getEncryptDataKeyFailoverFile(String envName, String dataId, String group, String tenant) {
        File tmp = new File(LOCAL_SNAPSHOT_PATH, envName + "_nacos");
        tmp = new File(tmp, "encrypted-data-key");
        
        if (StringUtils.isBlank(tenant)) {
            tmp = new File(tmp, "failover");
        } else {
            tmp = new File(tmp, "failover-tenant");
            tmp = new File(tmp, tenant);
        }
        
        return new File(new File(tmp, group), dataId);
    }
    
    private static File getEncryptDataKeySnapshotFile(String envName, String dataId, String group, String tenant) {
        File tmp = new File(LOCAL_SNAPSHOT_PATH, envName + "_nacos");
        tmp = new File(tmp, "encrypted-data-key");
        
        if (StringUtils.isBlank(tenant)) {
            tmp = new File(tmp, "snapshot");
        } else {
            tmp = new File(tmp, "snapshot-tenant");
            tmp = new File(tmp, tenant);
        }
        
        return new File(new File(tmp, group), dataId);
    }
    
}
