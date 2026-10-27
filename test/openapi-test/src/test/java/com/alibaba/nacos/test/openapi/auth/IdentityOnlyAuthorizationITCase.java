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

/**
 * Direct authentication matrix for Console operations tagged {@code ONLY_IDENTITY}.
 *
 * <p>These operations require a valid identity but intentionally skip resource authority
 * evaluation. The matrix therefore proves both sides of that contract: absent, invalid, and
 * blank credentials fail, while an authenticated user with no role permission reaches the
 * business operation. The same scenarios are used with merged and independent Console
 * deployments so remote identity-only delegation is covered as well.</p>
 *
 * @author Nacos
 */
public class IdentityOnlyAuthorizationITCase extends AuthITCase {

    private static final String CONSOLE_BASE = "http://" + NACOS_HOST + ':'
            + System.getProperty("nacos.console.port", "8080");

    @ParameterizedTest(name = "{0}")
    @MethodSource("identityOnlyScenarios")
    void testEveryConsoleIdentityOnlyOperation(IdentityOnlyScenario scenario) throws Exception {
        assertDenied(get(CONSOLE_BASE, scenario.path(), null));
        assertDenied(get(CONSOLE_BASE, scenario.path(), "invalid-token"));
        assertDenied(getWithAuthorization(CONSOLE_BASE, scenario.path(), ""));

        TestIdentity identity = createIdentityWithoutPermission("identity-only");
        assertSuccess(get(CONSOLE_BASE, scenario.path(), identity.token()));
    }

    private static Stream<IdentityOnlyScenario> identityOnlyScenarios() {
        return Stream.of(
                new IdentityOnlyScenario("ConsoleNamespaceController#getNamespaceList",
                        "/v3/console/core/namespace/list"),
                new IdentityOnlyScenario("ConsoleNamespaceController#checkNamespaceIdExist",
                        "/v3/console/core/namespace/exist?customNamespaceId=auth-missing"),
                new IdentityOnlyScenario("ConsoleServiceController#getSelectorTypeList",
                        "/v3/console/ns/service/selector/types"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sharedClientScenarios")
    void testSharedClientIdentityOnlyOperation(IdentityOnlyScenario scenario) throws Exception {
        assertDenied(get(SERVER_BASE_URL, scenario.path(), "invalid-token"));
        assertDenied(getWithAuthorization(SERVER_BASE_URL, scenario.path(), ""));
        Response authenticated = get(SERVER_BASE_URL, scenario.path(), noPermissionToken());
        assertNotEquals(403, authenticated.status(), authenticated.body());
        Response anonymous = get(SERVER_BASE_URL, scenario.path(), null);
        if (Boolean.parseBoolean(System.getProperty("nacos.test.auth.anonymous-ai.enabled", "false"))) {
            assertNotEquals(403, anonymous.status(), anonymous.body());
        } else {
            assertEquals(403, anonymous.status(), anonymous.body());
        }
    }

    private static Stream<IdentityOnlyScenario> sharedClientScenarios() {
        return Stream.of(
                new IdentityOnlyScenario("SkillClientController#searchShared",
                        "/nacos/v3/client/ai/skills/search?namespaceId=__nacos_ai_hub__&pageSize=1"),
                new IdentityOnlyScenario("SkillClientController#getShared",
                        "/nacos/v3/client/ai/skills?namespaceId=__nacos_ai_hub__&name=9223372036854775807"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sharedManagementScenarios")
    void testSharedManagementIdentityOnlyOperation(SharedScenario scenario) throws Exception {
        assertDenied(get(scenario.baseUrl(), scenario.path(), "invalid-token"));
        assertDenied(getWithAuthorization(scenario.baseUrl(), scenario.path(), ""));
        Response authenticated = get(scenario.baseUrl(), scenario.path(), noPermissionToken());
        assertNotEquals(403, authenticated.status(), authenticated.body());
        Response anonymous = get(scenario.baseUrl(), scenario.path(), null);
        if (Boolean.parseBoolean(System.getProperty("nacos.test.auth.anonymous-ai.enabled", "false"))) {
            assertNotEquals(403, anonymous.status(), anonymous.body());
        } else {
            assertEquals(403, anonymous.status(), anonymous.body());
        }
    }

    private static Stream<SharedScenario> sharedManagementScenarios() {
        return Stream.of("admin", "console").flatMap(audience -> {
            String controller = "admin".equals(audience) ? "SkillAdminController" : "ConsoleSkillController";
            String base = "admin".equals(audience) ? SERVER_BASE_URL : CONSOLE_BASE;
            String path = ("admin".equals(audience) ? "/nacos" : "") + "/v3/" + audience + "/ai/skills";
            String entry = "?namespaceId=__nacos_ai_hub__&skillName=9223372036854775807&version=1.0.0";
            return Stream.of(
                    new SharedScenario(controller + "#listSharedSkills", base,
                            path + "/list?namespaceId=__nacos_ai_hub__&pageSize=1"),
                    new SharedScenario(controller + "#getSharedSkill", base, path + entry),
                    new SharedScenario(controller + "#getSharedSkillVersion", base, path + "/version" + entry),
                    new SharedScenario(controller + "#downloadSharedSkillVersion", base,
                            path + "/version/download" + entry));
        });
    }

    private record SharedScenario(String name, String baseUrl, String path) {

        @Override
        public String toString() {
            return name;
        }
    }

    private record IdentityOnlyScenario(String name, String path) {

        @Override
        public String toString() {
            return name;
        }
    }
}
