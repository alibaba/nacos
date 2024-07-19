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

package com.alibaba.nacos.config.server.service.dump.disk;

import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.common.pathencoder.PathEncoderManager;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;

/**
 * config raw disk service.
 *
 * @author zunfei.lzf
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class ConfigRawDiskService implements ConfigDiskService {
    
    private static final String BASE_DIR = File.separator + "data" + File.separator + "config-data";
    
    private static final String TENANT_BASE_DIR = File.separator + "data" + File.separator + "tenant-config-data";
    
    private static final String BETA_DIR = File.separator + "data" + File.separator + "beta-data";
    
    private static final String TENANT_BETA_DIR = File.separator + "data" + File.separator + "tenant-beta-data";
    
    private static final String TAG_DIR = File.separator + "data" + File.separator + "tag-data";
    
    private static final String TENANT_TAG_DIR = File.separator + "data" + File.separator + "tenant-tag-data";
    
    private static final String BATCH_DIR = File.separator + "data" + File.separator + "batch-data";
    
    private static final String TENANT_BATCH_DIR = File.separator + "data" + File.separator + "tenant-batch-data";
    
    /**
     * Save configuration information to disk.
     */
    public void saveToDisk(String dataId, String group, String tenant, String content) throws IOException {
        File targetFile = targetFile(dataId, group, tenant);
        FileUtils.writeStringToFile(targetFile, content, ENCODE_UTF8);
    }
    
    /**
     * Returns the path of the server cache file.
     */
    private static File targetFile(String dataId, String group, String tenant) {
        // fix https://github.com/alibaba/nacos/issues/10067
        dataId = PathEncoderManager.getInstance().encode(dataId);
        group = PathEncoderManager.getInstance().encode(group);
        tenant = PathEncoderManager.getInstance().encode(tenant);
        File file = null;
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
    private static File targetBetaFile(String dataId, String group, String tenant) {
        // fix https://github.com/alibaba/nacos/issues/10067
        dataId = PathEncoderManager.getInstance().encode(dataId);
        group = PathEncoderManager.getInstance().encode(group);
        tenant = PathEncoderManager.getInstance().encode(tenant);
        File file = null;
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
    private static File targetTagFile(String dataId, String group, String tenant, String tag) {
        // fix https://github.com/alibaba/nacos/issues/10067
        dataId = PathEncoderManager.getInstance().encode(dataId);
        group = PathEncoderManager.getInstance().encode(group);
        tenant = PathEncoderManager.getInstance().encode(tenant);
        File file = null;
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
    
    /**
     * Save beta information to disk.
     */
    public void saveBetaToDisk(String dataId, String group, String tenant, String content) throws IOException {
        File targetFile = targetBetaFile(dataId, group, tenant);
        FileUtils.writeStringToFile(targetFile, content, ENCODE_UTF8);
    }
    
    /**
     * Save tag information to disk.
     */
    public void saveTagToDisk(String dataId, String group, String tenant, String tag, String content)
            throws IOException {
        File targetFile = targetTagFile(dataId, group, tenant, tag);
        FileUtils.writeStringToFile(targetFile, content, ENCODE_UTF8);
    }
    
    /**
     * Deletes configuration files on disk.
     */
    public void removeConfigInfo(String dataId, String group, String tenant) {
        FileUtils.deleteQuietly(targetFile(dataId, group, tenant));
    }
    
    /**
     * Deletes beta configuration files on disk.
     */
    public void removeConfigInfo4Beta(String dataId, String group, String tenant) {
        FileUtils.deleteQuietly(targetBetaFile(dataId, group, tenant));
    }
    
    /**
     * Deletes tag configuration files on disk.
     */
    public void removeConfigInfo4Tag(String dataId, String group, String tenant, String tag) {
        FileUtils.deleteQuietly(targetTagFile(dataId, group, tenant, tag));
    }
    
    private static String file2String(File file) throws IOException {
        if (!file.exists()) {
            return null;
        }
        return FileUtils.readFileToString(file, ENCODE_UTF8);
    }
    
    /**
     * Returns the path of cache file in server.
     */
    public String getBetaContent(String dataId, String group, String tenant) throws IOException {
        File file = targetBetaFile(dataId, group, tenant);
        return file2String(file);
    }
    
    /**
     * Returns the path of the tag cache file in server.
     */
    public String getTagContent(String dataId, String group, String tenant, String tag) throws IOException {
        File file = targetTagFile(dataId, group, tenant, tag);
        return file2String(file);
    }
    
    public String getContent(String dataId, String group, String tenant) throws IOException {
        File file = targetFile(dataId, group, tenant);
        if (file.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                return IoUtils.toString(fis, ENCODE_UTF8);
            } catch (FileNotFoundException e) {
                return null;
            } finally {
                IoUtils.closeQuietly(fis);
            }
        } else {
            return null;
        }
    }
    
    /**
     * Clear all config file.
     */
    public void clearAll() {
        File file = new File(EnvUtil.getNacosHome(), BASE_DIR);
        if (!file.exists() || FileUtils.deleteQuietly(file)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info failed.");
        }
        File fileTenant = new File(EnvUtil.getNacosHome(), TENANT_BASE_DIR);
        if (!fileTenant.exists() || FileUtils.deleteQuietly(fileTenant)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-tenant success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-tenant failed.");
        }
    }
    
    /**
     * Clear all beta config file.
     */
    public void clearAllBeta() {
        File file = new File(EnvUtil.getNacosHome(), BETA_DIR);
        if (!file.exists() || FileUtils.deleteQuietly(file)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-beta success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-beta failed.");
        }
        File fileTenant = new File(EnvUtil.getNacosHome(), TENANT_BETA_DIR);
        if (!fileTenant.exists() || FileUtils.deleteQuietly(fileTenant)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-beta-tenant success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-beta-tenant failed.");
        }
    }
    
    /**
     * Clear all tag config file.
     */
    public void clearAllTag() {
        File file = new File(EnvUtil.getNacosHome(), TAG_DIR);
        
        if (!file.exists() || FileUtils.deleteQuietly(file)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-tag success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-tag failed.");
        }
        File fileTenant = new File(EnvUtil.getNacosHome(), TENANT_TAG_DIR);
        if (!fileTenant.exists() || FileUtils.deleteQuietly(fileTenant)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-tag-tenant success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-tag-tenant failed.");
        }
    }
    
}
