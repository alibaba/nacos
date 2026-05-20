/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.result.code;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResultCodeEnumTest {
    
    @Test
    void testSuccess() {
        assertEquals(200, ResultCodeEnum.SUCCESS.getCode());
        assertNotNull(ResultCodeEnum.SUCCESS.getCodeMsg());
    }
    
    @Test
    void testError() {
        assertEquals(500, ResultCodeEnum.ERROR.getCode());
        assertNotNull(ResultCodeEnum.ERROR.getCodeMsg());
    }
    
    @Test
    void testNamespaceNotExist() {
        assertEquals(100001, ResultCodeEnum.NAMESPACE_NOT_EXIST.getCode());
    }
    
    @Test
    void testMetadataIllegal() {
        assertEquals(100002, ResultCodeEnum.METADATA_ILLEGAL.getCode());
    }
    
    @Test
    void testDataValidationFailed() {
        assertEquals(100003, ResultCodeEnum.DATA_VALIDATION_FAILED.getCode());
    }
    
    @Test
    void testParsingDataFailed() {
        assertEquals(100004, ResultCodeEnum.PARSING_DATA_FAILED.getCode());
    }
    
    @Test
    void testDataEmpty() {
        assertEquals(100005, ResultCodeEnum.DATA_EMPTY.getCode());
    }
    
    @Test
    void testNoSelectedConfig() {
        assertEquals(100006, ResultCodeEnum.NO_SELECTED_CONFIG.getCode());
    }
    
    @Test
    void testValuesLength() {
        assertEquals(8, ResultCodeEnum.values().length);
    }
}
