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
package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * FileService
 * @author rushsky518
 */
@Service
public class FileService {
    @Autowired
    private PersistService persistService;

    public void download(ZipOutputStream zos, String namespaceId, String group) throws IOException {
        zipFilesByPage(zos, namespaceId, group);
    }

    public void download(ZipOutputStream zos, String namespaceId, List files) throws IOException {
        if (null != files && !files.isEmpty()) {
            for (int i = 0; i< files.size(); i++) {
                Map map = (Map)files.get(i);
                String dataId = map.get("dataId").toString();
                String group = map.get("group").toString();

                ConfigInfo configInfo = persistService.findConfigInfo(dataId, group, namespaceId);
                if (null == configInfo) {
                    continue;
                }
                zipSingleFile(zos, new ByteArrayInputStream(configInfo.getContent().getBytes()), configInfo.getGroup() + ZIP_SEPARATOR + configInfo.getDataId());
            }
        } else {
            zipFilesByPage(zos, namespaceId, null);
        }
    }

    public void resolveZipFile(InputStream is, String namespaceId, String uploadMode) throws IOException {
        byte[] buffer = new byte[1024];
        int len = 0;
        ZipInputStream zis = new ZipInputStream(is);
        for (ZipEntry zipEntry = zis.getNextEntry(); null != zipEntry; zipEntry = zis.getNextEntry()) {
            if (zipEntry.isDirectory()) {
                continue;
            }

            String[] dirs = zipEntry.getName().split(ZIP_SEPARATOR);
            if (dirs.length != 2) {
                continue;
            }
            String dataId = dirs[1];
            String group = dirs[0];
            // query from db
            ConfigInfo cfDb = persistService.findConfigInfo(dataId, group, namespaceId);
            if (null == cfDb) {
                persistService.addConfigInfo(null, null, readConfigInfoFromZip(buffer, len, zis, dataId, group, namespaceId),
                    TimeUtils.getCurrentTime(), null, true);
            } else {
                if (Constants.UPLOAD_TERMINATE_MODE.equals(uploadMode)) {
                    break;
                } else if (Constants.UPLOAD_OVERRIDE_MODE.equals(uploadMode)) {
                    Timestamp time = TimeUtils.getCurrentTime();
                    persistService.updateConfigInfo(readConfigInfoFromZip(buffer, len, zis, dataId, group, namespaceId),
                        null, null,time, null, true);
                } else if (Constants.UPLOAD_SKIP_MODE.equals(uploadMode)) {
                    continue;
                }
            }
        }

        zis.closeEntry();
        zis.close();
    }

    private void zipFilesByPage( ZipOutputStream zos, String namespaceId, String group) throws IOException {
        int pageNo = 1;
        int pageSize = 20;

        Page<ConfigInfo> configInfos = persistService.findConfigInfo4Page(pageNo, pageSize, null, group, namespaceId, null);
        if (null == configInfos) {
            throw new RuntimeException("no config_info record found");
        }

        while(true) {
            for (int i = 0; i < configInfos.getPageItems().size(); i++) {
                ConfigInfo configInfo = configInfos.getPageItems().get(i);
                zipSingleFile(zos, new ByteArrayInputStream(configInfo.getContent().getBytes()), configInfo.getGroup() + ZIP_SEPARATOR + configInfo.getDataId());
            }
            configInfos = persistService.findConfigInfo4Page(++pageNo, pageSize, null, group, namespaceId, null);
            if (null == configInfos) {
                break;
            }
        }
    }

    private ConfigInfo readConfigInfoFromZip(byte[] buffer, int len, ZipInputStream zis, String dataId, String group, String namespaceId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        ConfigInfo cf = new ConfigInfo(dataId, group, baos.toString());
        baos.close();
        cf.setTenant(namespaceId);
        return cf;
    }

    private void zipSingleFile(ZipOutputStream zos, InputStream is, String fileName) throws IOException {
        ZipEntry zipEntry = new ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = is.read(bytes)) >= 0) {
            zos.write(bytes, 0, length);
        }
        is.close();
    }

    private final String ZIP_SEPARATOR = "/";
}

