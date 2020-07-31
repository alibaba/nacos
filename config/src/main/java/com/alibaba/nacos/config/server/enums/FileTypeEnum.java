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
    YML("yaml"),
    
    /**
     * Yaml file.
     */
    YAML("yaml"),
    
    /**
     * Text file.
     */
    TXT("text"),
    
    /**
     * Text file.
     */
    TEXT("text"),
    
    /**
     * Json file.
     */
    JSON("json"),
    
    /**
     * Xml file.
     */
    XML("xml"),
    
    /**
     * Html file.
     */
    HTM("html"),
    
    /**
     * Html file.
     */
    HTML("html"),
    
    /**
     * Properties file.
     */
    PROPERTIES("properties");
    
    /**
     * File type corresponding to file extension.
     */
    private String fileType;
    
    FileTypeEnum(String fileType) {
        this.fileType = fileType;
    }
    
    public String getFileType() {
        return this.fileType;
    }
}
