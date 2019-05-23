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
package com.alibaba.nacos.config.server.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.RestResult;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.config.server.service.DiskUtil;
import com.alibaba.nacos.config.server.service.FileService;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.StringUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

import static com.alibaba.nacos.core.utils.SystemUtils.NACOS_HOME;

/**
 * export and import config file
 * @author rushsky518
 */
@Controller
@RequestMapping(Constants.FILE_CONTROLLER_PATH)
public class FileController {

    private final PersistService persistService;

    private final  FileService fileService;

    @Autowired
    public FileController(PersistService persistService, FileService fileService) {
        this.persistService = persistService;
        this.fileService = fileService;
    }

    @RequestMapping(value = "/downloadNamespace", method = RequestMethod.GET)
    public ResponseEntity<byte[]> downloadNamespaceGroup(@RequestParam String namespaceId, @RequestParam(required = false) String group) throws IOException {
        String path = null;
        try {
            path = fileService.download(namespaceId, group);
        } catch (IOException e) {
            LogUtil.fatalLog.error("io exception occurs when download config file", e);
        } catch (RuntimeException e) {
            LogUtil.defaultLog.error("no config_info record found");
        }

        if (StringUtils.isBlank(path)) {
            return ResponseEntity.ok(new byte[0]);
        }

        String filename = path.substring(path.lastIndexOf(File.separator) + 1) ;
        File file = new File(path);
        // set http header
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("Content-Disposition", "attachment; filename=" + URLEncoder.encode(filename, "UTF-8"));
        return new ResponseEntity<>(FileUtils.readFileToByteArray(file), headers, HttpStatus.OK);
    }

    @RequestMapping(value = "/downloadMultiFiles", method = RequestMethod.GET)
    public ResponseEntity<byte[]> downloadMultiFiles(@RequestParam String param) throws IOException {
        JSONObject json = JSON.parseObject(param);
        String namespaceId = json.getString("namespaceId");
        List files = json.getJSONArray("files");
        if (StringUtils.isBlank(namespaceId)) {
            return ResponseEntity.ok(new byte[0]);
        }

        String path = null;
        try {
            path = fileService.download(namespaceId, files);
        } catch (IOException e) {
            LogUtil.fatalLog.error("io exception occurs when download config file", e);
        } catch (RuntimeException e) {
            LogUtil.defaultLog.error("no config_info record found");
        }
        if (StringUtils.isBlank(path)) {
            return ResponseEntity.ok(new byte[0]);
        }

        String filename = path.substring(path.lastIndexOf(File.separator) + 1) ;
        File file = new File(path);
        // set http header
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("Content-Disposition", "attachment; filename=" + URLEncoder.encode(filename, "UTF-8"));

        return new ResponseEntity<>(FileUtils.readFileToByteArray(file), headers, HttpStatus.OK);
    }

    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    @ResponseBody
    public RestResult upload(@RequestParam MultipartFile file,
                             @RequestParam(required = false, defaultValue = "") String namespaceId,
                             @RequestParam(required = false, defaultValue = "terminate") String uploadMode) throws IOException {
        if (!"".equals(namespaceId)) {
            TenantInfo tenant = persistService.findTenantByKp("1", namespaceId);
            if (null == tenant) {
                return new RestResult(0, "namespace does not exist");
            }
        }

        String zipFilePath = NACOS_HOME + DiskUtil.UPLOAD_DIR + File.separator + System.currentTimeMillis()+ File.separator + file.getOriginalFilename();
        // save to disk
        File zipFile = new File(zipFilePath);
        zipFile.getParentFile().mkdirs();
        file.transferTo(zipFile);
        fileService.resolveZipFile(zipFilePath, namespaceId, uploadMode);

        return new RestResult(0, "upload success");
    }

}
