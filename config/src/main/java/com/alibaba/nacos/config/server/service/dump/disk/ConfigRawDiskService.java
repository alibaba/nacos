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
import com.alibaba.nacos.common.utils.PathSafetyUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;

/**
 * config raw disk service.
 *
 * @author zunfei.lzf
 */
public class ConfigRawDiskService implements ConfigDiskService {
    
    private static final String CURRENT_DIRECTORY = ".";
    
    private static final String PARENT_DIRECTORY = "..";
    
    private static final String BASE_DIR = File.separator + "data" + File.separator + "config-data";
    
    private static final String TENANT_BASE_DIR =
        File.separator + "data" + File.separator + "tenant-config-data";
    
    private static final String GRAY_DIR = File.separator + "data" + File.separator + "gray-data";
    
    private static final String TENANT_GRAY_DIR =
        File.separator + "data" + File.separator + "tenant-gray-data";
    
    /**
     * Save configuration information to disk.
     */
    public void saveToDisk(String dataId, String group, String tenant, String content)
        throws IOException {
        File targetFile = targetFile(dataId, group, tenant);
        FileUtils.writeStringToFile(targetFile, content, ENCODE_UTF8);
    }
    
    /**
     * Returns the path of the server cache file.
     */
    static File targetFile(String dataId, String group, String tenant) {
        try {
            ParamUtils.checkParam(dataId, group, tenant);
        } catch (Exception e) {
            throw invalidIdentityException(dataId, group, tenant, e);
        }
        if (StringUtils.isBlank(tenant)) {
            return resolveTargetFile(BASE_DIR, group, dataId);
        }
        return resolveTargetFile(TENANT_BASE_DIR, tenant, group, dataId);
    }
    
    /**
     * Returns the path of the gray cache file in server.
     */
    private static File targetGrayFile(String dataId, String group, String tenant,
        String grayName) {
        try {
            ParamUtils.checkParam(grayName);
            ParamUtils.checkParam(dataId, group, tenant);
        } catch (Exception e) {
            if (StringUtils.isBlank(grayName) || !ParamUtils.isValid(grayName)) {
                throw invalidParameterException("grayName", grayName, e);
            }
            throw invalidIdentityException(dataId, group, tenant, e);
        }
        if (StringUtils.isBlank(tenant)) {
            return resolveTargetFile(GRAY_DIR, group, dataId, grayName);
        }
        return resolveTargetFile(TENANT_GRAY_DIR, tenant, group, dataId, grayName);
    }
    
    private static File resolveTargetFile(String baseDir, String... pathSegments) {
        Path current = new File(EnvUtil.getNacosHome(), baseDir).toPath().toAbsolutePath()
            .normalize();
        for (String pathSegment : pathSegments) {
            validatePathSegment(pathSegment);
            String encodedSegment = PathEncoderManager.getInstance().encode(pathSegment);
            try {
                current = PathSafetyUtils.resolveDirectChild(current, encodedSegment);
            } catch (IllegalArgumentException e) {
                throw invalidParameterException("encodedPathSegment", encodedSegment, e);
            }
        }
        return current.toFile();
    }
    
    private static void validatePathSegment(String pathSegment) {
        if (StringUtils.isBlank(pathSegment) || !ParamUtils.isValid(pathSegment)
            || isDirectoryControlSegment(pathSegment)) {
            throw invalidParameterException("pathSegment", pathSegment);
        }
    }
    
    private static boolean isDirectoryControlSegment(String pathSegment) {
        return CURRENT_DIRECTORY.equals(pathSegment) || PARENT_DIRECTORY.equals(pathSegment);
    }
    
    private static ConfigDiskPathException invalidIdentityException(String dataId, String group,
        String tenant, Throwable cause) {
        if (StringUtils.isBlank(dataId) || !ParamUtils.isValid(dataId)) {
            return invalidParameterException("dataId", dataId, cause);
        }
        if (StringUtils.isBlank(group) || !ParamUtils.isValid(group)) {
            return invalidParameterException("group", group, cause);
        }
        return invalidParameterException("namespaceId", tenant, cause);
    }
    
    private static ConfigDiskPathException invalidParameterException(String parameterName,
        String parameterValue) {
        return new ConfigDiskPathException(parameterName, parameterValue);
    }
    
    private static ConfigDiskPathException invalidParameterException(String parameterName,
        String parameterValue, Throwable cause) {
        return new ConfigDiskPathException(parameterName, parameterValue, cause);
    }
    
    /**
     * Returns the path of the gray content cache file in server.
     */
    private static File targetGrayContentFile(String dataId, String group, String tenant,
        String grayName) {
        return targetGrayFile(dataId, group, tenant, grayName);
    }
    
    /**
     * Save gray information to disk.
     */
    public void saveGrayToDisk(String dataId, String group, String tenant, String grayName,
        String content)
        throws IOException {
        File targetGrayContentFile = targetGrayContentFile(dataId, group, tenant, grayName);
        FileUtils.writeStringToFile(targetGrayContentFile, content, ENCODE_UTF8);
    }
    
    /**
     * Deletes configuration files on disk.
     */
    public void removeConfigInfo(String dataId, String group, String tenant) {
        FileUtils.deleteQuietly(targetFile(dataId, group, tenant));
    }
    
    /**
     * Deletes gray configuration files on disk.
     */
    public void removeConfigInfo4Gray(String dataId, String group, String tenant, String grayName) {
        FileUtils.deleteQuietly(targetGrayContentFile(dataId, group, tenant, grayName));
    }
    
    private static String file2String(File file) throws IOException {
        if (!file.exists()) {
            return null;
        }
        return FileUtils.readFileToString(file, ENCODE_UTF8);
    }
    
    /**
     * Returns the content of the gray cache file in server.
     */
    public String getGrayContent(String dataId, String group, String tenant, String grayName)
        throws IOException {
        return file2String(targetGrayContentFile(dataId, group, tenant, grayName));
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
     * Clear all gray config file.
     */
    public void clearAllGray() {
        File file = new File(EnvUtil.getNacosHome(), GRAY_DIR);
        
        if (!file.exists() || FileUtils.deleteQuietly(file)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-gray success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-gray failed.");
        }
        File fileTenant = new File(EnvUtil.getNacosHome(), TENANT_GRAY_DIR);
        if (!fileTenant.exists() || FileUtils.deleteQuietly(fileTenant)) {
            LogUtil.DEFAULT_LOG.info("clear all config-info-gray-tenant success.");
        } else {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-gray-tenant failed.");
        }
    }
    
}
