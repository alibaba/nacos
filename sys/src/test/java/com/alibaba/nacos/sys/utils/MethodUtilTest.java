/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.sys.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MethodUtilTest {
    
    private static final Method DOUBLE_METHOD;
    
    private static final Method LONG_METHOD;
    
    static {
        try {
            DOUBLE_METHOD = InternalMethod.class.getMethod("getD");
            LONG_METHOD = InternalMethod.class.getMethod("getL");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    void invokeAndReturnDouble() {
        InternalMethod internalMethod = new InternalMethod();
        assertNotEquals(Double.NaN, MethodUtil.invokeAndReturnDouble(DOUBLE_METHOD, internalMethod), 0.000001d);
        
        assertEquals(Double.NaN, MethodUtil.invokeAndReturnDouble(LONG_METHOD, internalMethod), 0.000001d);
    }
    
    @Test
    void invokeAndReturnLong() {
        InternalMethod internalMethod = new InternalMethod();
        assertEquals(100L, MethodUtil.invokeAndReturnLong(LONG_METHOD, internalMethod));
        assertNotEquals(100L, MethodUtil.invokeAndReturnLong(DOUBLE_METHOD, internalMethod));
    }
    
    public static class InternalMethod {
        
        private double d = 1.1d;
        
        private long l = 100L;
        
        public double getD() {
            return d;
        }
        
        public long getL() {
            return l;
        }
    }
}
