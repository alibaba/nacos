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

class RemoveConfigResultTest {

    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        RemoveConfigResult result = new RemoveConfigResult();
        assertFalse(result.isSuccess());
        assertEquals(0, result.getErrorCode());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("test success factory method")
    void testSuccessFactoryMethod() {
        RemoveConfigResult result = RemoveConfigResult.success();
        assertTrue(result.isSuccess());
        assertEquals(0, result.getErrorCode());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("test fail factory method")
    void testFailFactoryMethod() {
        RemoveConfigResult result = RemoveConfigResult.fail(500, "server error");
        assertFalse(result.isSuccess());
        assertEquals(500, result.getErrorCode());
        assertEquals("server error", result.getErrorMessage());
    }

    @Test
    @DisplayName("test getter and setter")
    void testGetterAndSetter() {
        RemoveConfigResult result = new RemoveConfigResult();
        result.setSuccess(true);
        result.setErrorCode(0);
        result.setErrorMessage(null);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getErrorCode());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("test toString")
    void testToString() {
        RemoveConfigResult result = RemoveConfigResult.fail(404, "not found");
        String str = result.toString();
        assertTrue(str.contains("RemoveConfigResult"));
        assertTrue(str.contains("false"));
        assertTrue(str.contains("404"));
        assertTrue(str.contains("not found"));
    }
}
