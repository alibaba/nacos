/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.model.v2;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class ErrorCodeTest {
    @Test
    public void testCodeNotSame() {
        Class<ErrorCode> errorCodeClass = ErrorCode.class;
        
        ErrorCode[] errorCodes = errorCodeClass.getEnumConstants();
        Set<Integer> codeSet = new HashSet<Integer>(errorCodes.length);
        
        for (ErrorCode errorCode : errorCodes) {
            codeSet.add(errorCode.getCode());
        }
        
        assertEquals(errorCodes.length, codeSet.size());
    }
}
