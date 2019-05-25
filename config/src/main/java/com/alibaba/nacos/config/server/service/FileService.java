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

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.alibaba.nacos.config.server.service.FileService.FileType.*;
import static com.alibaba.nacos.config.server.service.FileService.UploadPolicy.*;
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
                zipSingleFile(zos, new ByteArrayInputStream(configInfo.getContent().getBytes(UTF_8)), configInfo.getGroup() + ZIP_SEPARATOR + configInfo.getDataId());
                genMetaYmlContent(sb, configInfo);
            }

            if (sb.length() > 0) {
                zipSingleFile(zos, new ByteArrayInputStream(sb.toString().getBytes(UTF_8)), META_FILENAME);
            }
        } else {
            zipFilesByPage(zos, namespaceId, null);
        }
    }

    public void resolveZipFile(InputStream is, String namespaceId, String uploadMode) throws IOException {
        Map<String, String> metaMap = new HashMap<>(64);
        List<ConfigInfo> cfList = new ArrayList<>(64);
        resolveMetaAndConfig(is, namespaceId, metaMap, cfList);
        addOrUpdateConfig(namespaceId, uploadMode, metaMap, cfList);
    }

    private void resolveMetaAndConfig(InputStream is, String namespaceId, Map<String, String> metaMap, List<ConfigInfo> cfList) throws IOException {
        ZipInputStream zis = new ZipInputStream(is);
        for (ZipEntry zipEntry = zis.getNextEntry(); null != zipEntry; zipEntry = zis.getNextEntry()) {
            if (zipEntry.isDirectory()) {
                continue;
            }

            if (zipEntry.getName().equals(META_FILENAME)) {
                fillMetaMap(zis, metaMap);
                continue;
            }

            String[] dirs = zipEntry.getName().split(ZIP_SEPARATOR);
            if (dirs.length != 2) {
                continue;
            }
            cfList.add(readConfigInfoFromZip(zis, namespaceId, dirs[0], dirs[1]));
        }

        zis.closeEntry();
        zis.close();
    }

    private void addOrUpdateConfig(String namespaceId, String uploadMode,  Map<String, String> metaMap, List<ConfigInfo> cfList) {
        UploadPolicy policy = UploadPolicy.valueOf(uploadMode.toUpperCase());

        for (ConfigInfo cf: cfList) {
            // query from db
            ConfigInfo dbCf = persistService.findConfigInfo(cf.getDataId(), cf.getGroup(), namespaceId);
            if (null == dbCf) {
                String appname = metaMap.get(cf.getGroup() + cf.getDataId());
                cf.setAppName(appname == null ? "" : appname);
                Map<String, Object> advanceInfo = new HashMap<>(4);
                advanceInfo.put("type", getFileType(cf.getDataId()));

                persistService.addConfigInfo(null, null, cf, TimeUtils.getCurrentTime(), advanceInfo, false);
                continue;
            }

            if (ABORT.equals(policy)) {
                break;
            } else if (OVERWRITE.equals(policy)) {
                String appname = metaMap.get(cf.getGroup() + cf.getDataId());
                cf.setAppName(appname == null ? "" : appname);
                Timestamp time = TimeUtils.getCurrentTime();
                Map<String, Object> advanceInfo = new HashMap<>(4);
                advanceInfo.put("type", getFileType(cf.getDataId()));
                persistService.updateConfigInfo(cf,null, null,time, advanceInfo, true);
            } else if (SKIP.equals(policy)) { }
        }
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
            zipSingleFile(zos, new ByteArrayInputStream(sb.toString().getBytes(UTF_8)), META_FILENAME);
        }
    }

    private void fillMetaMap(ZipInputStream zis, Map<String, String> metaMap) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(zis));
        String line;
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
            metaMap.put(group + dataId, appname);
        }
    }

    private ConfigInfo readConfigInfoFromZip(ZipInputStream zis, String namespaceId, String group, String dataId) throws IOException {
        int len;
        byte[] buffer = new byte[512];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        ConfigInfo cf = new ConfigInfo(dataId, group, new String(baos.toByteArray(), UTF_8));
        cf.setTenant(namespaceId);
        baos.close();
        return cf;
    }

    private String getFileType(String dataId) {
        String type = TEXT.getValue();
        if (dataId.contains(JSON.getValue())) {
            type = JSON.getValue();
        } else if (dataId.contains(XML.getValue())) {
            type = XML.getValue();
        } else if (dataId.contains(YML.getValue()) || dataId.contains(YAML.getValue())) {
            type = YAML.getValue();
        } else if (dataId.contains(HTML.getValue()) || dataId.contains(HTM.getValue())) {
            type = HTML.getValue();
        } else if (dataId.contains(PROPERTIES.getValue())) {
            type = PROPERTIES.getValue();
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

    enum FileType {
        /** txt */
        TXT("txt"),
        /** text */
        TEXT("text"),
        /** json */
        JSON("json"),
        /** xml */
        XML("xml"),
        /** yml */
        YML("yml"),
        /** yaml */
        YAML("yaml"),
        /** html */
        HTML("html"),
        /** htm */
        HTM("htm"),
        /** properties */
        PROPERTIES("properties");

        private String value;
        FileType(String value) {
            this.value = value;
        }
        public String getValue() {
            return value;
        }
    }

    enum UploadPolicy {
        /** abort */
        ABORT,
        /** skip */
        SKIP,
        /** overwrite */
        OVERWRITE;
    }

    private final String WAVE = "~";
    private final String DOT = ".";
    private final String META_FILENAME = ".meta.yml";
    private final String ZIP_SEPARATOR = "/";
}
