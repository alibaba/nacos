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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Disk util.
 *
 * @author jiuRen
 */
public class DiskUtil {
    
    static final String BASE_DIR = File.separator + "data" + File.separator + "config-data";
    
    static final String TENANT_BASE_DIR = File.separator + "data" + File.separator + "tenant-config-data";
    
    static final String BETA_DIR = File.separator + "data" + File.separator + "beta-data";
    
    static final String TENANT_BETA_DIR = File.separator + "data" + File.separator + "tenant-beta-data";
    
    static final String TAG_DIR = File.separator + "data" + File.separator + "tag-data";
    
    static final String TENANT_TAG_DIR = File.separator + "data" + File.separator + "tag-beta-data";
    
    public static void saveHeartBeatToDisk(String heartBeatTime) throws IOException {
        FileUtils.writeStringToFile(heartBeatFile(), heartBeatTime, Constants.ENCODE);
    }
    
    /**
     * Save configuration information to disk.
     */
    public static void saveToDisk(String dataId, String group, String tenant, String content) throws IOException {
        File targetFile = targetFile(dataId, group, tenant);
        FileUtils.writeStringToFile(targetFile, content, Constants.ENCODE);
    }
    
    /**
     * Save beta information to disk.
     */
    public static void saveBetaToDisk(String dataId, String group, String tenant, String content) throws IOException {
        File targetFile = targetBetaFile(dataId, group, tenant);
        FileUtils.writeStringToFile(targetFile, content, Constants.ENCODE);
    }
    
    /**
     * Save tag information to disk.
     */
    public static void saveTagToDisk(String dataId, String group, String tenant, String tag, String content)
            throws IOException {
        File targetFile = targetTagFile(dataId, group, tenant, tag);
        FileUtils.writeStringToFile(targetFile, content, Constants.ENCODE);
    }
    
    /**
     * Deletes configuration files on disk.
     */
    public static void removeConfigInfo(String dataId, String group, String tenant) {
        FileUtils.deleteQuietly(targetFile(dataId, group, tenant));
    }
    
    /**
     * Deletes beta configuration files on disk.
     */
    public static void removeConfigInfo4Beta(String dataId, String group, String tenant) {
        FileUtils.deleteQuietly(targetBetaFile(dataId, group, tenant));
    }
    
    /**
     * Deletes tag configuration files on disk.
     */
    public static void removeConfigInfo4Tag(String dataId, String group, String tenant, String tag) {
        FileUtils.deleteQuietly(targetTagFile(dataId, group, tenant, tag));
    }
    
    public static void removeHeartHeat() {
        FileUtils.deleteQuietly(heartBeatFile());
    }
    
    /**
     * Returns the path of the server cache file.
     */
    public static File targetFile(String dataId, String group, String tenant) {
        File file;
        if (StringUtils.isBlank(tenant)) {
            file = new File(EnvUtil.getNacosHome(), BASE_DIR);
        } else {
            file = new File(EnvUtil.getNacosHome(), TENANT_BASE_DIR);
            file = new File(file, tenant);
        }
        file = new File(file, group);
        file = new File(file, dataId);
        return file;
    }
    
    /**
     * Returns the path of cache file in server.
     */
    public static File targetBetaFile(String dataId, String group, String tenant) {
        File file;
        if (StringUtils.isBlank(tenant)) {
            file = new File(EnvUtil.getNacosHome(), BETA_DIR);
        } else {
            file = new File(EnvUtil.getNacosHome(), TENANT_BETA_DIR);
            file = new File(file, tenant);
        }
        file = new File(file, group);
        file = new File(file, dataId);
        return file;
    }
    
    /**
     * Returns the path of the tag cache file in server.
     */
    public static File targetTagFile(String dataId, String group, String tenant, String tag) {
        File file;
        if (StringUtils.isBlank(tenant)) {
            file = new File(EnvUtil.getNacosHome(), TAG_DIR);
        } else {
            file = new File(EnvUtil.getNacosHome(), TENANT_TAG_DIR);
            file = new File(file, tenant);
        }
        file = new File(file, group);
        file = new File(file, dataId);
        file = new File(file, tag);
        return file;
    }
    
    public static String getConfig(String dataId, String group, String tenant) throws IOException {
        File file = targetFile(dataId, group, tenant);
        if (file.exists()) {
            
            try (FileInputStream fis = new FileInputStream(file);) {
                return IoUtils.toString(fis, Constants.ENCODE);
            } catch (FileNotFoundException e) {
                return StringUtils.EMPTY;
            }
        } else {
            return StringUtils.EMPTY;
        }
    }
    
    public static String getLocalConfigMd5(String dataId, String group, String tenant) throws IOException {
        return MD5Utils.md5Hex(getConfig(dataId, group, tenant), Constants.ENCODE);
    }
    
    public static File heartBeatFile() {
        return new File(EnvUtil.getNacosHome(), "status" + File.separator + "heartBeat.txt");
    }
    
    public static String relativePath(String dataId, String group) {
        return BASE_DIR + "/" + dataId + "/" + group;
    }
    
    /**
     * Clear all config file.
     */
    public static void clearAll() {
        File file = new File(EnvUtil.getNacosHome(), BASE_DIR);
        if (FileUtils.deleteQuietly(file)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info failed.");
        }
        File fileTenant = new File(EnvUtil.getNacosHome(), TENANT_BASE_DIR);
        if (FileUtils.deleteQuietly(fileTenant)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-tenant success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-tenant failed.");
        }
    }
    
    /**
     * Clear all beta config file.
     */
    public static void clearAllBeta() {
        File file = new File(EnvUtil.getNacosHome(), BETA_DIR);
        if (FileUtils.deleteQuietly(file)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-beta success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-beta failed.");
        }
        File fileTenant = new File(EnvUtil.getNacosHome(), TENANT_BETA_DIR);
        if (FileUtils.deleteQuietly(fileTenant)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-beta-tenant success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-beta-tenant failed.");
        }
    }
    
    /**
     * Clear all tag config file.
     */
    public static void clearAllTag() {
        File file = new File(EnvUtil.getNacosHome(), TAG_DIR);
        if (FileUtils.deleteQuietly(file)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-tag success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-tag failed.");
        }
        File fileTenant = new File(EnvUtil.getNacosHome(), TENANT_TAG_DIR);
        if (FileUtils.deleteQuietly(fileTenant)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-tag-tenant success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-tag-tenant failed.");
        }
    }
}
