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

package com.alibaba.nacos.console.aot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.ResourceHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.SerializationHints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosRuntimeHintsTest {
    
    @Mock
    RuntimeHints runtimeHints;
    
    @Mock
    ResourceHints resourceHints;
    
    @Mock
    SerializationHints serializationHints;
    
    @Mock
    ReflectionHints reflectionHints;
    
    NacosRuntimeHints nacosRuntimeHints;
    
    @BeforeEach
    void setUp() throws ClassNotFoundException {
        nacosRuntimeHints = new NacosRuntimeHints();
        when(runtimeHints.resources()).thenReturn(resourceHints);
        when(runtimeHints.serialization()).thenReturn(serializationHints);
        when(runtimeHints.reflection()).thenReturn(reflectionHints);
    }
    
    @Test
    void registerHints() {
        nacosRuntimeHints.registerHints(runtimeHints, null);
        verify(resourceHints, atLeastOnce()).registerPattern(anyString());
        verify(serializationHints, atLeastOnce()).registerType(any(Class.class));
        verify(reflectionHints, atLeastOnce()).registerType(any(Class.class), eq(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS),
                eq(MemberCategory.INVOKE_DECLARED_METHODS), eq(MemberCategory.DECLARED_FIELDS),
                eq(MemberCategory.DECLARED_CLASSES));
    }
}