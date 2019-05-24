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
import com.alibaba.nacos.config.server.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

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
            StringBuilder sb = new StringBuilder(512);
            for (int i = 0; i< files.size(); i++) {
                Map map = (Map)files.get(i);
                String dataId = map.get("dataId").toString();
                String group = map.get("group").toString();

                ConfigInfo configInfo = persistService.findConfigInfo(dataId, group, namespaceId);
                if (null == configInfo) {
                    continue;
                }
                zipSingleFile(zos, new ByteArrayInputStream(configInfo.getContent().getBytes()), configInfo.getGroup() + ZIP_SEPARATOR + configInfo.getDataId());
                genMetaYmlContent(sb, configInfo);
            }

            if (sb.length() > 0) {
                zipSingleFile(zos, new ByteArrayInputStream(sb.toString().getBytes()), META_FILENAME);
            }
        } else {
            zipFilesByPage(zos, namespaceId, null);
        }
    }

    public void resolveZipFile(InputStream is, String namespaceId, String uploadMode) throws IOException {
        ByteArrayOutputStream baos = cloneInputStream(is);
        InputStream metaInputStream = new ByteArrayInputStream(baos.toByteArray());
        InputStream configInputStream = new ByteArrayInputStream(baos.toByteArray());
        baos.close();

        Map<String, String> metaMap = resolveMetaFile(metaInputStream);
        resolveConfigFile(configInputStream, namespaceId, uploadMode, metaMap);
    }

    private static ByteArrayOutputStream cloneInputStream(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            return baos;
        } finally {
            in.close();
        }
    }

    public Map<String, String> resolveMetaFile(InputStream is) throws IOException {
        Map<String, String> map = new HashMap<>(64);
        ZipInputStream zis = new ZipInputStream(is);
        for (ZipEntry zipEntry = zis.getNextEntry(); null != zipEntry; zipEntry = zis.getNextEntry()) {
            if (zipEntry.getName().equals(META_FILENAME)) {
                BufferedReader br = new BufferedReader(new InputStreamReader(zis));
                String line = null;
                while ((line = br.readLine()) != null) {
                    if (StringUtils.isBlank(line)) {
                        continue;
                    }
                    // example: DEFAULT_GROUP.biz~properties.app=appname
                    String[] arr = line.split("\\.");
                    if (3 != arr.length) {
                        continue;
                    }

                    String group = arr[0];
                    String dataId = arr[1].replace(WAVE, DOT);
                    String appname = arr[2].split("=")[1];
                    map.put(group + dataId, appname);
                }
                br.close();
                break;
            }
        }

        return map;
    }

    public void resolveConfigFile(InputStream is, String namespaceId, String uploadMode, Map<String, String> metaMap) throws IOException {
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
            ConfigInfo dbCf = persistService.findConfigInfo(dataId, group, namespaceId);
            if (null == dbCf) {
                ConfigInfo cf = readConfigInfoFromZip(buffer, len, zis, dataId, group, namespaceId, metaMap);
                Map<String, Object> advanceInfo = new HashMap<>(4);
                advanceInfo.put("type", getFileType(dataId));
                persistService.addConfigInfo(null, null, cf, TimeUtils.getCurrentTime(), advanceInfo, false);
            } else {
                if (Constants.UPLOAD_TERMINATE_MODE.equals(uploadMode)) {
                    break;
                } else if (Constants.UPLOAD_OVERRIDE_MODE.equals(uploadMode)) {
                    Timestamp time = TimeUtils.getCurrentTime();
                    ConfigInfo cf = readConfigInfoFromZip(buffer, len, zis, dataId, group, namespaceId, metaMap);
                    Map<String, Object> advanceInfo = new HashMap<>(4);
                    advanceInfo.put("type", getFileType(dataId));
                    persistService.updateConfigInfo(cf,null, null,time, advanceInfo, true);
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
            return;
        }

        StringBuilder sb = new StringBuilder(512);
        while(true) {
            for (int i = 0; i < configInfos.getPageItems().size(); i++) {
                ConfigInfo configInfo = configInfos.getPageItems().get(i);
                zipSingleFile(zos, new ByteArrayInputStream(configInfo.getContent().getBytes(UTF_8)), configInfo.getGroup() + ZIP_SEPARATOR + configInfo.getDataId());
                genMetaYmlContent(sb,configInfo);
            }
            configInfos = persistService.findConfigInfo4Page(++pageNo, pageSize, null, group, namespaceId, null);
            if (null == configInfos) {
                break;
            }
        }

        if (sb.length() > 0) {
            zipSingleFile(zos, new ByteArrayInputStream(sb.toString().getBytes()), META_FILENAME);
        }
    }

    private ConfigInfo readConfigInfoFromZip(byte[] buffer, int len, ZipInputStream zis, String dataId, String group, String namespaceId, Map<String, String> metaMap) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        ConfigInfo cf = new ConfigInfo(dataId, group, new String(baos.toByteArray(), UTF_8));
        baos.close();
        cf.setTenant(namespaceId);
        String appname = metaMap.get(group + dataId);
        cf.setAppName(null != appname ? appname : "");
        return cf;
    }

    private String getFileType(String dataId) {
        String type = "text";
        if (dataId.contains("json")) {
            type = "json";
        } else if (dataId.contains("xml")) {
            type = "xml";
        } else if (dataId.contains("yml") || dataId.contains("yaml")) {
            type = "yaml";
        } else if (dataId.contains("html") || dataId.contains("htm")) {
            type = "text/html";
        } else if (dataId.contains("properties")) {
            type = "properties";
        }
        return type;
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
        zos.closeEntry();
    }

    private void genMetaYmlContent(StringBuilder sb, ConfigInfo ci) {
        if (null == ci || StringUtils.isBlank(ci.getAppName())) {
            return;
        }

        sb.append(ci.getGroup()).append(DOT)
            .append(ci.getDataId().replace(DOT, WAVE)).append(DOT)
            .append("app=").append(ci.getAppName())
            .append("\n");
    }

    private final String WAVE = "~";
    private final String DOT = ".";
    private final String META_FILENAME = ".meta.yml";
    private final String ZIP_SEPARATOR = "/";
}
