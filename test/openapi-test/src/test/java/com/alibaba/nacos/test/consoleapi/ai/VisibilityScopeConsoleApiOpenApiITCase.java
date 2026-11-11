/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.consoleapi.ai;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import org.junit.jupiter.api.Test;

/**
 * Default scope validation boundary for Agent, Skill and AgentSpec list and scope-update APIs.
 *
 * <p>Unregistered scopes return HTTP 400/PARAMETER_VALIDATE_ERROR before resource lookup;
 * existing lifecycle ITs cover PUBLIC/PRIVATE and required/optional parameters.
 */
public class VisibilityScopeConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {
    
    @Test
    public void testAgentRejectsUnregisteredScope() throws Exception {
        assertError(getRaw(CONSOLE_AGENT_LIST_PATH,
            Query.newInstance().addParam("scope", "TEAM_EDIT")), 400,
            ErrorCode.PARAMETER_VALIDATE_ERROR, "scope");
        assertError(putRaw(CONSOLE_AGENT_PATH + "/scope",
            Query.newInstance().addParam("agentName", randomAiName("scope-extension"))
                .addParam("scope", "TEAM_EDIT")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "scope");
    }
    
    @Test
    public void testSkillRejectsUnregisteredScope() throws Exception {
        assertError(
            getRaw(CONSOLE_SKILL_LIST_PATH, Query.newInstance().addParam("scope", "TEAM_EDIT")),
            400, ErrorCode.PARAMETER_VALIDATE_ERROR, "scope");
        assertError(putRaw(CONSOLE_SKILL_PATH + "/scope",
            queryFrom(skillScopeForm(randomAiName("scope-extension"), "TEAM_EDIT"))),
            400, ErrorCode.PARAMETER_VALIDATE_ERROR, "scope");
    }
    
    @Test
    public void testAgentSpecRejectsUnregisteredScope() throws Exception {
        assertError(
            getRaw(CONSOLE_AGENT_SPEC_LIST_PATH,
                Query.newInstance().addParam("scope", "TEAM_EDIT")),
            400, ErrorCode.PARAMETER_VALIDATE_ERROR, "scope");
        assertError(putRaw(CONSOLE_AGENT_SPEC_PATH + "/scope",
            queryFrom(agentSpecScopeForm(randomAiName("scope-extension"), "TEAM_EDIT"))),
            400, ErrorCode.PARAMETER_VALIDATE_ERROR, "scope");
    }
}
