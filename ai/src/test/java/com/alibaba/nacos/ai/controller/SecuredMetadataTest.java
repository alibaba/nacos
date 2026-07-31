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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecuredMetadataTest {
    
    @Test
    void testAdminAiForcePublishMetadata() {
        assertSecured(AgentAdminController.class, "forcePublish",
            "/v3/admin/ai/agents/force-publish");
        assertSecured(AgentSpecAdminController.class, "forcePublish",
            "/v3/admin/ai/agentspecs/force-publish");
        assertSecured(PromptAdminController.class, "forcePublish",
            "/v3/admin/ai/prompt/force-publish");
        assertSecured(SkillAdminController.class, "forcePublish",
            "/v3/admin/ai/skills/force-publish");
    }
    
    private void assertSecured(Class<?> controllerClass, String methodName, String resource) {
        Method method = Arrays.stream(controllerClass.getDeclaredMethods())
            .filter(candidate -> methodName.equals(candidate.getName()))
            .findFirst()
            .orElseThrow();
        Secured secured = method.getAnnotation(Secured.class);
        assertNotNull(secured);
        assertEquals(resource, secured.resource());
        assertEquals(ActionTypes.WRITE, secured.action());
        assertEquals(SignType.AI, secured.signType());
        assertEquals(ApiType.ADMIN_API, secured.apiType());
    }
}
