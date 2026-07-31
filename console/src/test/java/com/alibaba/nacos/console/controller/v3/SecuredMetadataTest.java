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

package com.alibaba.nacos.console.controller.v3;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.console.controller.v3.ai.ConsoleA2aController;
import com.alibaba.nacos.console.controller.v3.ai.ConsoleAgentController;
import com.alibaba.nacos.console.controller.v3.ai.ConsoleAgentSpecController;
import com.alibaba.nacos.console.controller.v3.ai.ConsoleCopilotConfigController;
import com.alibaba.nacos.console.controller.v3.ai.ConsolePromptController;
import com.alibaba.nacos.console.controller.v3.ai.ConsoleSkillController;
import com.alibaba.nacos.console.controller.v3.config.ConsoleConfigController;
import com.alibaba.nacos.console.controller.v3.core.ConsoleClusterController;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecuredMetadataTest {
    
    @Test
    void testConsoleManagementResources() {
        assertSecured(ConsoleClusterController.class, "getNodeList",
            "/v3/console/core/cluster/nodes", ActionTypes.READ, SignType.CONSOLE);
        assertSecured(ConsoleCopilotConfigController.class, "getConfig",
            "console/copilot/config", ActionTypes.READ, SignType.AI);
        assertSecured(ConsoleCopilotConfigController.class, "saveConfig",
            "console/copilot/config", ActionTypes.WRITE, SignType.AI);
        assertSecured(ConsoleAgentController.class, "forcePublish",
            "/v3/console/ai/agents/force-publish", ActionTypes.WRITE, SignType.AI);
    }
    
    @Test
    void testConsoleApiTypesAndTypedResources() {
        assertSecured(ConsoleConfigController.class, "getAllSubClientConfigByIp",
            "", ActionTypes.READ, SignType.CONFIG);
        assertSecured(ConsoleConfigController.class, "stopBeta",
            "", ActionTypes.WRITE, SignType.CONFIG);
        assertSecured(ConsoleConfigController.class, "queryBeta",
            "", ActionTypes.READ, SignType.CONFIG);
        assertSecured(ConsoleA2aController.class, "listAgentVersions",
            "", ActionTypes.READ, SignType.AI);
    }
    
    @Test
    void testConsoleAiForcePublishSignTypes() {
        assertSecured(ConsoleAgentSpecController.class, "forcePublish",
            "console/agentspecs", ActionTypes.WRITE, SignType.AI);
        assertSecured(ConsolePromptController.class, "forcePublish",
            "/v3/console/ai/prompt/force-publish", ActionTypes.WRITE, SignType.AI);
        assertSecured(ConsoleSkillController.class, "forcePublish",
            "console/skills", ActionTypes.WRITE, SignType.AI);
    }
    
    private void assertSecured(Class<?> controllerClass, String methodName, String resource,
        ActionTypes action, String signType) {
        Method method = Arrays.stream(controllerClass.getDeclaredMethods())
            .filter(candidate -> methodName.equals(candidate.getName()))
            .findFirst()
            .orElseThrow();
        Secured secured = method.getAnnotation(Secured.class);
        assertNotNull(secured);
        assertEquals(resource, secured.resource());
        assertEquals(action, secured.action());
        assertEquals(signType, secured.signType());
        assertEquals(ApiType.CONSOLE_API, secured.apiType());
    }
}
