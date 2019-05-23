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
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.alibaba.nacos.config.server.service.DiskUtil.DOWANLOAD_DIR;
import static com.alibaba.nacos.config.server.service.DiskUtil.UPLOAD_DIR;
import static com.alibaba.nacos.core.utils.SystemUtils.NACOS_HOME;

/**
 * FileService
 * @author rushsky518
 */
@Service
public class FileService {
    @Autowired
    private PersistService persistService;

    public String download(String namespaceId, String group) throws IOException {
        cleanTmpFiles(DOWNLOAD_MODE);

        String zipName = getZipName(namespaceId);
        String srcDirPath = getRootPath(zipName);
        File srcDir = new File(srcDirPath);
        String dstFullFilename = srcDirPath + ".zip";

        saveFilesByPage(srcDirPath, group, namespaceId);

        // zip file
        zipFile(dstFullFilename, srcDir);

        return dstFullFilename;
    }

    public String download(String namespaceId, List files) throws IOException {
        cleanTmpFiles(DOWNLOAD_MODE);

        String zipName = getZipName(namespaceId);
        String srcDirPath = getRootPath(zipName);
        File srcDir = new File(srcDirPath);
        String dstFullFilename = srcDirPath + ".zip";

        // write files to disk
        if (null != files && !files.isEmpty()) {
            for (int i = 0; i< files.size(); i++) {
                Map map = (Map)files.get(i);
                String dataId = map.get("dataId").toString();
                String group = map.get("group").toString();

                ConfigInfo configInfo = persistService.findConfigInfo(dataId, group, namespaceId);
                if (null == configInfo) {
                    continue;
                }
                saveToDownloadDir(srcDirPath, configInfo.getGroup(), configInfo.getDataId(), configInfo.getContent());
            }
        } else {
            saveFilesByPage(srcDirPath, null, namespaceId);
        }

        // zip file
        zipFile(dstFullFilename, srcDir);

        return dstFullFilename;
    }

    public void resolveZipFile(String zipFilePath, String namespaceId, String uploadMode) throws IOException {
        cleanTmpFiles(UPLOAD_MODE);

        byte[] buffer = new byte[1024];
        int len = 0;
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
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

    private void saveFilesByPage(String srcDirPath, String group, String namespaceId) throws IOException {
        int pageNo = 1;
        int pageSize = 20;

        Page<ConfigInfo> configInfos = persistService.findConfigInfo4Page(pageNo, pageSize, null, group, namespaceId, null);
        if (null == configInfos) {
            throw new RuntimeException("no config_info record found");
        }

        while(true) {
            for (int i = 0; i < configInfos.getPageItems().size(); i++) {
                ConfigInfo configInfo = configInfos.getPageItems().get(i);
                saveToDownloadDir(srcDirPath, configInfo.getGroup(), configInfo.getDataId(), configInfo.getContent());
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

    private void cleanTmpFiles(String mode) {
        File dir = null;
        if (DOWNLOAD_MODE.equals(mode)) {
            dir = new File(NACOS_HOME + DOWANLOAD_DIR);
        } else if (UPLOAD_MODE.equals(mode)){
            dir = new File(NACOS_HOME + UPLOAD_DIR);
        }

        File[] files = dir.listFiles();
        if (null == files || 0 == files.length) {
            return;
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < files.length; i++) {
            if (now - files[i].lastModified() > 3600 * 1000) {
                FileUtils.deleteQuietly(files[i]);
            }
        }

        LogUtil.defaultLog.info("clean download[upload] directory");
    }

    private String getZipName(String namespaceId) {
        namespaceId = StringUtils.isBlank(namespaceId) ? "public" : namespaceId;
        return namespaceId + "_" + System.currentTimeMillis();
    }

    private String getRootPath(String zipName) {
        return NACOS_HOME + DOWANLOAD_DIR + File.separator  + zipName;
    }

    private void zipFile(String dstFullFilename, File srcDir) throws IOException {
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new FileOutputStream(dstFullFilename));
            zipFiles(srcDir.listFiles(), zos);
        } finally {
            if (null != zos) {
                try {
                    zos.close();
                } catch (IOException e) {
                    LogUtil.fatalLog.error("io exception occurs when close ZipOutputStream", e);
                }
            }
        }
        LogUtil.defaultLog.info("generate config zip file:{}", dstFullFilename);
    }

    private void zipFiles(File[] filesToZip, ZipOutputStream zipOut) throws IOException {
        if (null == filesToZip) {
            return;
        }
        for (File fileToZip : filesToZip) {
            zipFile(fileToZip, fileToZip.getName(), zipOut);
        }
    }

    private void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }

        if (fileToZip.isDirectory()) {
            zipOut.putNextEntry(new ZipEntry(fileName.endsWith(ZIP_SEPARATOR) ? fileName : fileName + ZIP_SEPARATOR));
            zipOut.closeEntry();

            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + ZIP_SEPARATOR + childFile.getName(), zipOut);
            }
            return;
        }

        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }

    private void saveToDownloadDir(String dirPath, String group, String dataId, String content) throws IOException {
        String filePath = dirPath + File.separator + group + File.separator + dataId;
        FileUtils.writeStringToFile(new File(filePath), content, Constants.ENCODE);
    }

    private final String ZIP_SEPARATOR = "/";
    private final String DOWNLOAD_MODE = "download";
    private final String UPLOAD_MODE = "upload";
}

