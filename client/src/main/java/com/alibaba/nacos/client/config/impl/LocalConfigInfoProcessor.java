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
import com.alibaba.nacos.client.config.utils.ConcurrentDiskUtil;
import com.alibaba.nacos.client.config.utils.JvmUtil;
import com.alibaba.nacos.client.config.utils.SnapShotSwitch;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Local Disaster Recovery Directory Tool.
 *
 * @author Nacos
 */
public class LocalConfigInfoProcessor {
    
    private static final Logger LOGGER = LogUtils.logger(LocalConfigInfoProcessor.class);
    
    public static String getFailover(String serverName, String dataId, String group, String tenant) {
        File localPath = getFailoverFile(serverName, dataId, group, tenant);
        if (!localPath.exists() || !localPath.isFile()) {
            return null;
        }
        
        try {
            return readFile(localPath);
        } catch (IOException ioe) {
            LOGGER.error("[" + serverName + "] get failover error, " + localPath, ioe);
            return null;
        }
    }
    
    /**
     * 获取本地缓存文件内容。NULL表示没有本地文件或抛出异常.
     */
    public static String getSnapshot(String name, String dataId, String group, String tenant) {
        if (!SnapShotSwitch.getIsSnapShot()) {
            return null;
        }
        File file = getSnapshotFile(name, dataId, group, tenant);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        
        try {
            return readFile(file);
        } catch (IOException ioe) {
            LOGGER.error("[" + name + "]+get snapshot error, " + file, ioe);
            return null;
        }
    }
    
    private static String readFile(File file) throws IOException {
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        
        if (JvmUtil.isMultiInstance()) {
            return ConcurrentDiskUtil.getFileContent(file, Constants.ENCODE);
        } else {
            InputStream is = null;
            try {
                is = new FileInputStream(file);
                return IoUtils.toString(is, Constants.ENCODE);
            } finally {
                try {
                    if (null != is) {
                        is.close();
                    }
                } catch (IOException ignored) {
                }
            }
        }
    }
    
    /**
     * Save snapshot.
     *
     * @param envName env name
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param config  config
     */
    public static void saveSnapshot(String envName, String dataId, String group, String tenant, String config) {
        if (!SnapShotSwitch.getIsSnapShot()) {
            return;
        }
        File file = getSnapshotFile(envName, dataId, group, tenant);
        if (null == config) {
            try {
                IoUtils.delete(file);
            } catch (IOException ioe) {
                LOGGER.error("[" + envName + "] delete snapshot error, " + file, ioe);
            }
        } else {
            try {
                File parentFile = file.getParentFile();
                if (!parentFile.exists()) {
                    boolean isMdOk = parentFile.mkdirs();
                    if (!isMdOk) {
                        LOGGER.error("[{}] save snapshot error", envName);
                    }
                }
                
                if (JvmUtil.isMultiInstance()) {
                    ConcurrentDiskUtil.writeFileContent(file, config, Constants.ENCODE);
                } else {
                    IoUtils.writeStringToFile(file, config, Constants.ENCODE);
                }
            } catch (IOException ioe) {
                LOGGER.error("[" + envName + "] save snapshot error, " + file, ioe);
            }
        }
    }
    
    /**
     * 清除snapshot目录下所有缓存文件.
     */
    public static void cleanAllSnapshot() {
        try {
            File rootFile = new File(LOCAL_SNAPSHOT_PATH);
            File[] files = rootFile.listFiles();
            if (files == null || files.length == 0) {
                return;
            }
            for (File file : files) {
                if (file.getName().endsWith("_nacos")) {
                    IoUtils.cleanDirectory(file);
                }
            }
        } catch (IOException ioe) {
            LOGGER.error("clean all snapshot error, " + ioe.toString(), ioe);
        }
    }
    
    /**
     * Clean snapshot.
     *
     * @param envName env name
     */
    public static void cleanEnvSnapshot(String envName) {
        File tmp = new File(LOCAL_SNAPSHOT_PATH, envName + "_nacos");
        tmp = new File(tmp, "snapshot");
        try {
            IoUtils.cleanDirectory(tmp);
            LOGGER.info("success delete " + envName + "-snapshot");
        } catch (IOException e) {
            LOGGER.info("fail delete " + envName + "-snapshot, " + e.toString());
            e.printStackTrace();
        }
    }
    
    static File getFailoverFile(String serverName, String dataId, String group, String tenant) {
        File tmp = new File(LOCAL_SNAPSHOT_PATH, serverName + "_nacos");
        tmp = new File(tmp, "data");
        if (StringUtils.isBlank(tenant)) {
            tmp = new File(tmp, "config-data");
        } else {
            tmp = new File(tmp, "config-data-tenant");
            tmp = new File(tmp, tenant);
        }
        return new File(new File(tmp, group), dataId);
    }
    
    static File getSnapshotFile(String envName, String dataId, String group, String tenant) {
        File tmp = new File(LOCAL_SNAPSHOT_PATH, envName + "_nacos");
        if (StringUtils.isBlank(tenant)) {
            tmp = new File(tmp, "snapshot");
        } else {
            tmp = new File(tmp, "snapshot-tenant");
            tmp = new File(tmp, tenant);
        }
        
        return new File(new File(tmp, group), dataId);
    }
    
    public static final String LOCAL_FILEROOT_PATH;
    
    public static final String LOCAL_SNAPSHOT_PATH;
    
    static {
        LOCAL_FILEROOT_PATH =
                System.getProperty("JM.LOG.PATH", System.getProperty("user.home")) + File.separator + "nacos"
                        + File.separator + "config";
        LOCAL_SNAPSHOT_PATH =
                System.getProperty("JM.SNAPSHOT.PATH", System.getProperty("user.home")) + File.separator + "nacos"
                        + File.separator + "config";
        LOGGER.info("LOCAL_SNAPSHOT_PATH:{}", LOCAL_SNAPSHOT_PATH);
    }
    
}
