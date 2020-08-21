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

package com.alibaba.nacos.config.server.enums;

import com.alibaba.nacos.common.http.param.MediaType;

/**
 * Config file type enum.
 *
 * @author klw
 * @date 2019/7/1 10:21
 */
public enum FileTypeEnum {
    
    /**
     * Yaml file.
     */
    YML("yaml", MediaType.TEXT_PLAIN),
    
    /**
     * Yaml file.
     */
    YAML("yaml", MediaType.TEXT_PLAIN),
    
    /**
     * Text file.
     */
    TXT("text", MediaType.TEXT_PLAIN),
    
    /**
     * Text file.
     */
    TEXT("text", MediaType.TEXT_PLAIN),
    
    /**
     * Json file.
     */
    JSON("json", MediaType.APPLICATION_JSON),
    
    /**
     * Xml file.
     */
    XML("xml", MediaType.APPLICATION_XML),
    
    /**
     * Html file.
     */
    HTM("html", MediaType.TEXT_HTML),
    
    /**
     * Html file.
     */
    HTML("html", MediaType.TEXT_HTML),
    
    /**
     * Properties file.
     */
    PROPERTIES("properties", MediaType.TEXT_PLAIN);
    
    /**
     * File type corresponding to file extension.
     */
    private String fileType;
    
    /**
     * Http Content type corresponding to file extension.
     */
    private String contentType;
    
    FileTypeEnum(String fileType) {
        this.fileType = fileType;
        this.contentType = MediaType.TEXT_PLAIN;
    }
    
    FileTypeEnum(String fileType, String contentType) {
        this.fileType = fileType;
        this.contentType = contentType;
    }
    
    public String getFileType() {
        return this.fileType;
    }
    
    public String getContentType() {
        return contentType;
    }
}
