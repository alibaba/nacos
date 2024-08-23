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

package com.alibaba.nacos.test.common;

import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This class contains integration tests for the FileTypeEnum enumeration. It verifies the functionality of the
 * getFileTypeEnumByFileExtensionOrFileType method. The tests ensure that the FileTypeEnum correctly maps file
 * extensions and file types to their respective enum values.
 *
 * @author by jiangmin.wu on 2020/12/30
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class FileTypeEnumCoreITCase {
    
    @Test
    void fileTypeEnumTest1() {
        for (FileTypeEnum value : FileTypeEnum.values()) {
            FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(value.name());
            assertEquals(fileTypeEnum, value);
        }
    }
    
    @Test
    void fileTypeEnumTest2() {
        for (FileTypeEnum value : FileTypeEnum.values()) {
            FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(value.getFileType());
            assertNotNull(fileTypeEnum);
        }
    }
    
    @Test
    void fileTypeEnumTest3() {
        FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType("t");
        assertEquals(FileTypeEnum.TEXT, fileTypeEnum);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType("");
        assertEquals(FileTypeEnum.TEXT, fileTypeEnum);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(".");
        assertEquals(FileTypeEnum.TEXT, fileTypeEnum);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType("1");
        assertEquals(FileTypeEnum.TEXT, fileTypeEnum);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(null);
        assertEquals(FileTypeEnum.TEXT, fileTypeEnum);
    }
    
}
