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
 * Encrypted data key (EncryptedDataKey) local snapshot, disaster recovery directory related.
 *
 * @author luyanbo(RobberPhex)
 */
public class LocalEncryptedDataKeyProcessor extends LocalConfigInfoProcessor {
    
    private static final Logger LOGGER = LogUtils.logger(LocalEncryptedDataKeyProcessor.class);
    
    private static final String FAILOVER_CHILD_1 = "encrypted-data-key";
    
    private static final String FAILOVER_CHILD_2 = "failover";
    
    private static final String FAILOVER_CHILD_3 = "failover-tenant";
    
    private static final String SNAPSHOT_CHILD_1 = "encrypted-data-key";
    
    private static final String SNAPSHOT_CHILD_2 = "snapshot";
    
    private static final String SNAPSHOT_CHILD_3 = "snapshot-tenant";
    
    private static final String SUFFIX = "_nacos";
    
    /**
     * Obtain the EncryptedDataKey of the disaster recovery configuration. NULL means there is no local file or an exception is thrown.
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
     * Get the EncryptedDataKey of the locally cached file. NULL means there is no local file or an exception is thrown.
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
     * Save the snapshot of encryptDataKey. If the content is NULL, delete the snapshot.
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
        File tmp = new File(LOCAL_SNAPSHOT_PATH, envName + SUFFIX);
        tmp = new File(tmp, FAILOVER_CHILD_1);
        
        if (StringUtils.isBlank(tenant)) {
            tmp = new File(tmp, FAILOVER_CHILD_2);
        } else {
            tmp = new File(tmp, FAILOVER_CHILD_3);
            tmp = new File(tmp, tenant);
        }
        
        return new File(new File(tmp, group), dataId);
    }
    
    private static File getEncryptDataKeySnapshotFile(String envName, String dataId, String group, String tenant) {
        File tmp = new File(LOCAL_SNAPSHOT_PATH, envName + SUFFIX);
        tmp = new File(tmp, SNAPSHOT_CHILD_1);
        
        if (StringUtils.isBlank(tenant)) {
            tmp = new File(tmp, SNAPSHOT_CHILD_2);
        } else {
            tmp = new File(tmp, SNAPSHOT_CHILD_3);
            tmp = new File(tmp, tenant);
        }
        
        return new File(new File(tmp, group), dataId);
    }
    
}
