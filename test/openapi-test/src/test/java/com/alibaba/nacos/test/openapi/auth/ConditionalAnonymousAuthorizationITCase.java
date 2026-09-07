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

package com.alibaba.nacos.test.openapi.auth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Direct authentication matrix for every operation tagged {@code ALLOW_ANONYMOUS}.
 *
 * <p>An absent credential may enter the business path when anonymous AI access is enabled, but
 * an explicit invalid or blank credential must fail authentication instead of falling back to
 * the anonymous identity.</p>
 *
 * @author Nacos
 */
public class ConditionalAnonymousAuthorizationITCase extends AuthITCase {

    private static final String ARD_BASE_URL = "http://" + NACOS_HOST + ':'
            + System.getProperty("nacos.ai.registry.port", "9080");

    @ParameterizedTest(name = "{0}")
    @MethodSource("anonymousScenarios")
    void testEveryConditionalAnonymousOperation(AnonymousScenario scenario) throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getProperty(
                        "nacos.test.auth.anonymous-ai.enabled", "false")),
                "Conditional anonymous scenarios require anonymous AI access");

        Response anonymous = execute(scenario, null);
        assertNotEquals(403, anonymous.status(), scenario + ": " + anonymous.body());

        Response invalid = execute(scenario, "Bearer invalid-token");
        assertEquals(scenario.rejectionStatus(), invalid.status(),
                scenario + ": " + invalid.body());

        Response blank = execute(scenario, "");
        assertEquals(scenario.rejectionStatus(), blank.status(),
                scenario + ": " + blank.body());
    }

    private Response execute(AnonymousScenario scenario, String authorization) throws Exception {
        if (scenario.method() == RequestMethod.POST) {
            return postJsonWithAuthorization(scenario.baseUrl(), scenario.path(), authorization,
                    "{}");
        }
        return requestWithAuthorization(scenario.method(), scenario.baseUrl(), scenario.path(),
                authorization);
    }

    private static Stream<AnonymousScenario> anonymousScenarios() {
        String missing = "auth-anonymous-missing";
        return Stream.of(
                server("AgentSpecAdminController#listAgentSpecs", RequestMethod.GET,
                        "/nacos/v3/admin/ai/agentspecs/list?namespaceId=public&pageNo=1&pageSize=1"),
                server("AgentSpecClientController#get", RequestMethod.GET,
                        "/nacos/v3/client/ai/agentspecs?namespaceId=public&name=" + missing),
                server("SkillAdminController#listSkills", RequestMethod.GET,
                        "/nacos/v3/admin/ai/skills/list?namespaceId=public&pageNo=1&pageSize=1"),
                server("SkillClientController#get", RequestMethod.GET,
                        "/nacos/v3/client/ai/skills?namespaceId=public&name=" + missing),
                ard("ArdWellKnownController#catalog", RequestMethod.GET,
                        "/.well-known/ai-catalog.json"),
                ard("ArdSearchController#search", RequestMethod.POST,
                        "/v3/ai/ard/search?namespaceId=public"),
                ard("ArdSearchController#explore", RequestMethod.POST,
                        "/v3/ai/ard/explore?namespaceId=public"),
                ard("ArdSearchController#catalog", RequestMethod.GET,
                        "/v3/ai/ard/ai-catalog.json?namespaceId=public"),
                ard("ArdSearchController#agents", RequestMethod.GET,
                        "/v3/ai/ard/agents?namespaceId=public&pageSize=1"),
                ard("ArdSearchController#artifact", RequestMethod.GET,
                        "/v3/ai/ard/artifacts?namespaceId=public&resourceType=skill"
                                + "&resourceName=" + missing + "&version=1.0.0"));
    }

    private static AnonymousScenario server(String name, RequestMethod method, String path) {
        return new AnonymousScenario(name, method, SERVER_BASE_URL, path, 403);
    }

    private static AnonymousScenario ard(String name, RequestMethod method, String path) {
        return new AnonymousScenario(name, method, ARD_BASE_URL, path, 401);
    }

    private record AnonymousScenario(String name, RequestMethod method, String baseUrl,
                                     String path, int rejectionStatus) {

        @Override
        public String toString() {
            return name;
        }
    }
}
