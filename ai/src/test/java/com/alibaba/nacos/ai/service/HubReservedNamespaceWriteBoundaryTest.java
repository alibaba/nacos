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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityOperationService;
import com.alibaba.nacos.ai.service.agent.AgentOperationService;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationServiceImpl;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityOperationService;
import com.alibaba.nacos.ai.service.prompt.PromptOperationServiceImpl;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Every ordinary resource mutation must reject the Hub namespace before touching collaborators. */
class HubReservedNamespaceWriteBoundaryTest {
    
    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("ordinaryWrites")
    void rejectsReservedNamespaceBeforeStorageOrDelegation(Class<?> type, Method method)
        throws Exception {
        Constructor<?> constructor = type.getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
            .map(parameter -> mock(parameter)).toArray();
        Object service = constructor.newInstance(dependencies);
        // Every String is deliberately invalid except the reserved namespace. Namespace rejection
        // must happen before resource validation, storage routing, or compatibility-mode selection.
        Object[] arguments = Arrays.stream(method.getParameterTypes()).map(parameter -> {
            if (parameter == String.class) {
                return AiConstants.Hub.NAMESPACE_ID;
            }
            if (parameter == boolean.class) {
                return false;
            }
            return null;
        }).toArray();
        InvocationTargetException invocation = assertThrows(InvocationTargetException.class,
            () -> method.invoke(service, arguments));
        NacosApiException failure =
            assertInstanceOf(NacosApiException.class, invocation.getCause());
        assertEquals(NacosException.INVALID_PARAM, failure.getErrCode());
        assertEquals(ErrorCode.ILLEGAL_NAMESPACE.getCode(), failure.getDetailErrCode());
        verifyNoInteractions(dependencies);
    }
    
    static Stream<Arguments> ordinaryWrites() {
        return Stream.of(PromptOperationServiceImpl.class, AgentSpecOperationServiceImpl.class,
            AgentOperationService.class, McpCompatibilityOperationService.class,
            A2aCompatibilityOperationService.class)
            .flatMap(type -> Arrays.stream(type.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().matches(
                    "(create|upload|bootstrap|update|delete|submit|publish|forcePublish|redraft|change|bind|unbind|refresh|"
                        + "register|release|write|online|offline).*"))
                .filter(method -> Arrays.asList(method.getParameterTypes()).contains(String.class))
                .map(method -> Arguments.of(type, method)));
    }
}
