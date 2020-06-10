/*
 *
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
 *
 */
package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * 磁盘操作工具类。
 * <p>
 * 只有一个dump线程。
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

    static public void saveHeartBeatToDisk(String heartBeatTime)
        throws IOException {
        FileUtils.writeStringToFile(heartBeatFile(), heartBeatTime,
            Constants.ENCODE);
    }

    /**
     * 保存配置信息到磁盘
     */
    static public void saveToDisk(String dataId, String group, String tenant, String content) throws IOException {
        File targetFile = targetFile(dataId, group, tenant);
        FileUtils.writeStringToFile(targetFile, content, Constants.ENCODE);
    }

    /**
     * 保存配置信息到磁盘
     */
    static public void saveBetaToDisk(String dataId, String group, String tenant, String content) throws IOException {
        File targetFile = targetBetaFile(dataId, group, tenant);
        FileUtils.writeStringToFile(targetFile, content, Constants.ENCODE);
    }

    /**
     * 保存配置信息到磁盘
     */
    static public void saveTagToDisk(String dataId, String group, String tenant, String tag, String content)
        throws IOException {
        File targetFile = targetTagFile(dataId, group, tenant, tag);
        FileUtils.writeStringToFile(targetFile, content, Constants.ENCODE);
    }

    /**
     * 删除磁盘上的配置文件
     */
    static public void removeConfigInfo(String dataId, String group, String tenant) {
        FileUtils.deleteQuietly(targetFile(dataId, group, tenant));
    }

    /**
     * 删除磁盘上的配置文件
     */
    static public void removeConfigInfo4Beta(String dataId, String group, String tenant) {
        FileUtils.deleteQuietly(targetBetaFile(dataId, group, tenant));
    }

    /**
     * 删除磁盘上的配置文件
     */
    static public void removeConfigInfo4Tag(String dataId, String group, String tenant, String tag) {
        FileUtils.deleteQuietly(targetTagFile(dataId, group, tenant, tag));
    }

    static public void removeHeartHeat() {
        FileUtils.deleteQuietly(heartBeatFile());
    }

    /**
     * 返回服务端缓存文件的路径
     */
    static public File targetFile(String dataId, String group, String tenant) {
        File file = null;
        if (StringUtils.isBlank(tenant)) {
            file = new File(ApplicationUtils.getNacosHome(), BASE_DIR);
        } else {
            file = new File(ApplicationUtils.getNacosHome(), TENANT_BASE_DIR);
            file = new File(file, tenant);
        }
        file = new File(file, group);
        file = new File(file, dataId);
        return file;
    }

    /**
     * 返回服务端beta缓存文件的路径
     */
    static public File targetBetaFile(String dataId, String group, String tenant) {
        File file = null;
        if (StringUtils.isBlank(tenant)) {
            file = new File(ApplicationUtils.getNacosHome(), BETA_DIR);
        } else {
            file = new File(ApplicationUtils.getNacosHome(), TENANT_BETA_DIR);
            file = new File(file, tenant);
        }
        file = new File(file, group);
        file = new File(file, dataId);
        return file;
    }

    /**
     * 返回服务端Tag缓存文件的路径
     */
    static public File targetTagFile(String dataId, String group, String tenant, String tag) {
        File file = null;
        if (StringUtils.isBlank(tenant)) {
            file = new File(ApplicationUtils.getNacosHome(), TAG_DIR);
        } else {
            file = new File(ApplicationUtils.getNacosHome(), TENANT_TAG_DIR);
            file = new File(file, tenant);
        }
        file = new File(file, group);
        file = new File(file, dataId);
        file = new File(file, tag);
        return file;
    }

    static public String getConfig(String dataId, String group, String tenant)
        throws IOException {
        File file = targetFile(dataId, group, tenant);
        if (file.exists()) {

            try(FileInputStream fis = new FileInputStream(file);) {
                return IoUtils.toString(fis, Constants.ENCODE);
            } catch (FileNotFoundException e) {
                return StringUtils.EMPTY;
            }
        } else {
            return StringUtils.EMPTY;
        }
    }

    static public String getLocalConfigMd5(String dataId, String group, String tenant)
        throws IOException {
        return MD5Utils.md5Hex(getConfig(dataId, group, tenant), Constants.ENCODE);
    }

    static public File heartBeatFile() {
        return new File(ApplicationUtils.getNacosHome(), "status" + File.separator + "heartBeat.txt");
    }

    static public String relativePath(String dataId, String group) {
        return BASE_DIR + "/" + dataId + "/" + group;
    }

    static public void clearAll() {
        File file = new File(ApplicationUtils.getNacosHome(), BASE_DIR);
        if (FileUtils.deleteQuietly(file)) {
            LogUtil.defaultLog.info("clear all config-info success.");
        } else {
            LogUtil.defaultLog.warn("clear all config-info failed.");
        }
        File fileTenant = new File(ApplicationUtils.getNacosHome(), TENANT_BASE_DIR);
        if (FileUtils.deleteQuietly(fileTenant)) {
            LogUtil.defaultLog.info("clear all config-info-tenant success.");
        } else {
            LogUtil.defaultLog.warn("clear all config-info-tenant failed.");
        }
    }

    static public void clearAllBeta() {
        File file = new File(ApplicationUtils.getNacosHome(), BETA_DIR);
        if (FileUtils.deleteQuietly(file)) {
            LogUtil.defaultLog.info("clear all config-info-beta success.");
        } else {
            LogUtil.defaultLog.warn("clear all config-info-beta failed.");
        }
        File fileTenant = new File(ApplicationUtils.getNacosHome(), TENANT_BETA_DIR);
        if (FileUtils.deleteQuietly(fileTenant)) {
            LogUtil.defaultLog.info("clear all config-info-beta-tenant success.");
        } else {
            LogUtil.defaultLog.warn("clear all config-info-beta-tenant failed.");
        }
    }

    static public void clearAllTag() {
        File file = new File(ApplicationUtils.getNacosHome(), TAG_DIR);
        if (FileUtils.deleteQuietly(file)) {
            LogUtil.defaultLog.info("clear all config-info-tag success.");
        } else {
            LogUtil.defaultLog.warn("clear all config-info-tag failed.");
        }
        File fileTenant = new File(ApplicationUtils.getNacosHome(), TENANT_TAG_DIR);
        if (FileUtils.deleteQuietly(fileTenant)) {
            LogUtil.defaultLog.info("clear all config-info-tag-tenant success.");
        } else {
            LogUtil.defaultLog.warn("clear all config-info-tag-tenant failed.");
        }
    }
}
