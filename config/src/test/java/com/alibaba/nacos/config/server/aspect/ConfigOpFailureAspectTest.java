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

package com.alibaba.nacos.config.server.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigOpFailureAspectTest {
    
    @Mock
    private JoinPoint joinPoint;
    
    @Mock
    private Signature signature;
    
    @Test
    void testLogExceptionWithArgs() {
        ConfigOpFailureAspect aspect = new ConfigOpFailureAspect();
        when(joinPoint.getArgs()).thenReturn(new Object[] {"arg1", "arg2", "arg3"});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        assertDoesNotThrow(
            () -> aspect.logException(joinPoint,
                new RuntimeException("test error")));
    }
    
    @Test
    void testLogExceptionWithNullArgs() {
        ConfigOpFailureAspect aspect = new ConfigOpFailureAspect();
        when(joinPoint.getArgs()).thenReturn(null);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        assertDoesNotThrow(
            () -> aspect.logException(joinPoint,
                new RuntimeException("test error")));
    }
    
    @Test
    void testLogExceptionWithSingleArg() {
        ConfigOpFailureAspect aspect = new ConfigOpFailureAspect();
        when(joinPoint.getArgs()).thenReturn(new Object[] {"onlyArg"});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        assertDoesNotThrow(
            () -> aspect.logException(joinPoint,
                new RuntimeException("test error")));
    }
    
    @Test
    void testLogExceptionWhenInternalError() {
        ConfigOpFailureAspect aspect = new ConfigOpFailureAspect();
        when(joinPoint.getArgs()).thenThrow(new RuntimeException("inner error"));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        assertDoesNotThrow(
            () -> aspect.logException(joinPoint,
                new RuntimeException("test error")));
    }
    
    @Test
    void testConfigRepositoryInterfaceMethods() {
        ConfigOpFailureAspect aspect = new ConfigOpFailureAspect();
        assertDoesNotThrow(aspect::configRepositoryInterfaceMethods);
    }
}
