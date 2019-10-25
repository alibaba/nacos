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
 * @author klw
 * @ClassName: FileTypeEnum
 * @Description: config file type enum
 * @date 2019/7/1 10:21
 */
public enum FileTypeEnum {

    /**
     * @author klw
     * @Description: yaml file
     */
    YML("yaml"),

    /**
     * @author klw
     * @Description: yaml file
     */
    YAML("yaml"),

    /**
     * @author klw
     * @Description: text file
     */
    TXT("text"),

    /**
     * @author klw
     * @Description: text file
     */
    TEXT("text"),

    /**
     * @author klw
     * @Description: json file
     */
    JSON("json"),

    /**
     * @author klw
     * @Description: xml file
     */
    XML("xml"),

    /**
     * @author klw
     * @Description: html file
     */
    HTM("html"),

    /**
     * @author klw
     * @Description: html file
     */
    HTML("html"),

    /**
     * @author klw
     * @Description: properties file
     */
    PROPERTIES("properties");

    /**
     * @author klw
     * @Description: file type corresponding to file extension
     */
    private String fileType;

    FileTypeEnum(String fileType) {
        this.fileType = fileType;
    }

    public String getFileType() {
        return this.fileType;
    }


}
