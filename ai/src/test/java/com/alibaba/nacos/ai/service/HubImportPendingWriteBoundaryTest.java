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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.agent.AgentOperationService;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationServiceImpl;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleOperationService;
import com.alibaba.nacos.ai.service.mcp.McpResourceLocator;
import com.alibaba.nacos.ai.service.prompt.PromptOperationServiceImpl;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.skills.SkillOperationServiceImpl;
import com.alibaba.nacos.ai.utils.AiResourceImportUtils;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Ordinary edits and deletes cannot race failed-import cleanup, regardless of visibility plugins. */
class HubImportPendingWriteBoundaryTest {
    
    @ParameterizedTest(name = "{0}.{2}")
    @MethodSource("mutations")
    void pendingImportRejectsMutationsBeforeSideEffects(Class<?> type, String resourceType,
        String methodName, Object[] arguments) throws Exception {
        AiResource pending = new AiResource();
        pending.setNamespaceId("target-ns");
        pending.setName("target");
        pending.setType(resourceType);
        pending.setStatus("disable");
        pending.setExt(AiResourceImportUtils.pendingExt("{\"origin\":{}}",
            Map.of("storage", "descriptor", "files", java.util.List.of("owned-key"))));
        Constructor<?> constructor = type.getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
            .map(parameter -> mock(parameter)).toArray();
        for (Object dependency : dependencies) {
            if (dependency instanceof AiResourceManager) {
                AiResourceManager manager = (AiResourceManager) dependency;
                when(manager.findMeta("target-ns", "target", resourceType)).thenReturn(pending);
                when(manager.requireMeta("target-ns", "target", resourceType)).thenReturn(pending);
            } else if (dependency instanceof McpResourceLocator) {
                when(((McpResourceLocator) dependency).locate("target-ns", "target", null))
                    .thenReturn(pending);
            }
        }
        Object service = constructor.newInstance(dependencies);
        Method method = Arrays.stream(type.getMethods())
            .filter(candidate -> candidate.getName().equals(methodName)
                && candidate.getParameterCount() == arguments.length)
            .findFirst().orElseThrow();
        InvocationTargetException invocation = assertThrows(InvocationTargetException.class,
            () -> method.invoke(service, arguments));
        assertEquals(NacosException.CONFLICT,
            assertInstanceOf(NacosException.class, invocation.getCause()).getErrCode());
        assertFalse(VisibilityHelper.canWriteResource(pending));
        for (Object dependency : dependencies) {
            if (!(dependency instanceof AiResourceManager)
                && !(dependency instanceof McpResourceLocator)) {
                verifyNoInteractions(dependency);
            }
        }
    }
    
    static Stream<Arguments> mutations() {
        return Stream.of(
            Arguments.of(SkillOperationServiceImpl.class, "skill", "deleteSkill",
                new Object[] {"target-ns", "target"}),
            Arguments.of(SkillOperationServiceImpl.class, "skill", "updateBizTags",
                new Object[] {"target-ns", "target", "new-tags"}),
            Arguments.of(AgentSpecOperationServiceImpl.class, "agentspec", "deleteAgentSpec",
                new Object[] {"target-ns", "target"}),
            Arguments.of(AgentSpecOperationServiceImpl.class, "agentspec", "updateBizTags",
                new Object[] {"target-ns", "target", "new-tags"}),
            Arguments.of(PromptOperationServiceImpl.class, "prompt", "deletePrompt",
                new Object[] {"target-ns", "target"}),
            Arguments.of(PromptOperationServiceImpl.class, "prompt", "updateDraft",
                new Object[] {"target-ns", "target", "user saved template", null, "edit"}),
            Arguments.of(AgentOperationService.class, "agent", "deleteAgent",
                new Object[] {"target-ns", "target"}),
            Arguments.of(AgentOperationService.class, "agent", "updateScope",
                new Object[] {"target-ns", "target", "PUBLIC"}),
            Arguments.of(McpLifecycleOperationService.class, "mcp", "deleteMcpServerDraft",
                new Object[] {"target-ns", "target", "0.0.1"}),
            Arguments.of(McpLifecycleOperationService.class, "mcp", "updateMcpServerStatus",
                new Object[] {"target-ns", "target", true}));
    }
}
