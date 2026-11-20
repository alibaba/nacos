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

package com.alibaba.nacos.plugin.datasource.impl.enums.h2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * TrustedH2FunctionEnumTest.
 *
 * @author blake.qiu
 */
public class TrustedH2FunctionEnumTest {
    
    @Test
    void testGetFunctionByName() {
        Assertions.assertEquals("CURRENT_TIMESTAMP",
            TrustedH2FunctionEnum.getFunctionByName("NOW()"));
    }
    
    @Test()
    void testGetFunctionByErrorName() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> TrustedH2FunctionEnum.getFunctionByName("UNKNOWN"));
    }
}
