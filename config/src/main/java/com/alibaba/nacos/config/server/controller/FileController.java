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

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.DiskUtil;
import com.alibaba.nacos.config.server.service.FileService;
import com.alibaba.nacos.config.server.utils.StringUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.core.utils.SystemUtils.NACOS_HOME;

/**
 * export and import config file
 * @author rushsky518
 */
@Controller
@RequestMapping(Constants.FILE_CONTROLLER_PATH)
public class FileController {

    private final transient FileService fileService;

    @Autowired
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @RequestMapping(value = "/download", method = RequestMethod.GET)
    public ResponseEntity<byte[]> downloadNamespaceGroup(@RequestParam String namespaceId, @RequestParam String group) throws IOException {
        String path = fileService.download(namespaceId, group);

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

    @RequestMapping(value = "/download", method = RequestMethod.POST, consumes="application/json")
    public ResponseEntity<byte[]> download(@RequestBody Map<String, Object> param) throws IOException {
        String namespace = param.get("namespaceId").toString();
        List files = (List)param.get("files");

        String path = fileService.download(namespace, files);
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
    public void upload(@RequestParam("file") CommonsMultipartFile file,
                         @RequestParam("namespaceId") String namespaceId,
                         @RequestParam(name = "uploadMode", defaultValue = "terminate") String uploadMode) throws IOException {
        long time = System.currentTimeMillis();
        String zipFilePath = NACOS_HOME + DiskUtil.UPLOAD_DIR + File.separator + time + File.separator + file.getOriginalFilename();

        // save to disk
        File zipFile = new File(zipFilePath);
        file.transferTo(zipFile);

        fileService.resolveZipFile(zipFilePath, namespaceId, uploadMode);
    }

}
