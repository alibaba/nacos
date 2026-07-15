/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.ram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RamConstantsTest {
    
    @Test
    void testConstructor() {
        assertNotNull(new RamConstants());
    }
    
    @Test
    void testFields() {
        assertEquals("signatureVersion", RamConstants.SIGNATURE_VERSION);
        assertEquals("v4", RamConstants.V4);
        assertEquals("HmacSHA256", RamConstants.SIGNATURE_V4_METHOD);
        assertEquals("mse-nacos", RamConstants.SIGNATURE_V4_PRODUCE);
    }
}
