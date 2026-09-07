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

/**
 * Direct authentication matrix for Console operations tagged {@code ONLY_IDENTITY}.
 *
 * <p>These operations require a valid identity but intentionally skip resource authority
 * evaluation. The matrix therefore proves both sides of that contract: absent, invalid, and
 * blank credentials fail, while an authenticated user with no role permission reaches the
 * business operation.</p>
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

    private record IdentityOnlyScenario(String name, String path) {

        @Override
        public String toString() {
            return name;
        }
    }
}
