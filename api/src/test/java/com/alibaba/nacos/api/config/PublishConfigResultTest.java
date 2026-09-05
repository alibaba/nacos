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

package com.alibaba.nacos.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishConfigResultTest {

    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        PublishConfigResult result = new PublishConfigResult();
        assertFalse(result.isSuccess());
        assertEquals(0, result.getErrorCode());
        assertNull(result.getErrorMessage());
        assertNull(result.getMd5());
    }

    @Test
    @DisplayName("test success factory method")
    void testSuccessFactoryMethod() {
        PublishConfigResult result = PublishConfigResult.success();
        assertTrue(result.isSuccess());
        assertEquals(0, result.getErrorCode());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("test success factory method with md5")
    void testSuccessFactoryMethodWithMd5() {
        PublishConfigResult result = PublishConfigResult.success("published-md5-hash");
        assertTrue(result.isSuccess());
        assertEquals("published-md5-hash", result.getMd5());
    }

    @Test
    @DisplayName("test fail factory method")
    void testFailFactoryMethod() {
        PublishConfigResult result = PublishConfigResult.fail(403, "no permission");
        assertFalse(result.isSuccess());
        assertEquals(403, result.getErrorCode());
        assertEquals("no permission", result.getErrorMessage());
    }

    @Test
    @DisplayName("test getter and setter")
    void testGetterAndSetter() {
        PublishConfigResult result = new PublishConfigResult();
        result.setSuccess(true);
        result.setErrorCode(0);
        result.setErrorMessage(null);
        result.setMd5("md5-hash");

        assertTrue(result.isSuccess());
        assertEquals(0, result.getErrorCode());
        assertNull(result.getErrorMessage());
        assertEquals("md5-hash", result.getMd5());
    }

    @Test
    @DisplayName("test toString")
    void testToString() {
        PublishConfigResult result = PublishConfigResult.success("hash123");
        String str = result.toString();
        assertTrue(str.contains("PublishConfigResult"));
        assertTrue(str.contains("true"));
        assertTrue(str.contains("hash123"));
    }
}
