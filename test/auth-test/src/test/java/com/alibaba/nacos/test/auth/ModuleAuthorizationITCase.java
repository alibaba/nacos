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

package com.alibaba.nacos.test.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Authorization scenarios for every non-anonymous secured Nacos HTTP controller.
 *
 * <p>Each scenario verifies missing identity, invalid identity, valid identity without
 * authority, and valid identity with the required authority. Controller APIs whose resource
 * starts with {@code console/} intentionally require a global administrator and therefore use
 * the administrator as the authorized identity.</p>
 *
 * @author Nacos
 */
public class ModuleAuthorizationITCase extends AuthITCase {

    private static final Set<String> AUTH_PLUGIN_CONTROLLERS = Set.of(
            "PermissionControllerV3", "RoleControllerV3", "UserControllerV3",
            "VisibilityGrantControllerV3");

    private static final Set<String> ANONYMOUS_ONLY_CONTROLLERS = Set.of(
            "ArdSearchController", "ArdWellKnownController", "SkillClientController");

    @ParameterizedTest(name = "{0}")
    @MethodSource("controllerScenarios")
    void testControllerAuthorization(ControllerScenario scenario) throws Exception {
        assertDenied(request(scenario.method(), scenario.baseUrl(), scenario.path(), null));
        assertDenied(request(scenario.method(), scenario.baseUrl(), scenario.path(),
                "invalid-token"));

        TestIdentity identity = createIdentityWithoutPermission(scenario.identityPrefix());
        assertDenied(request(scenario.method(), scenario.baseUrl(), scenario.path(),
                identity.token()));

        String authorizedToken;
        if (scenario.globalAdminOnly()) {
            authorizedToken = adminToken();
        } else {
            grantPermission(identity, "*", scenario.action());
            authorizedToken = identity.token();
        }
        Response authorized = request(scenario.method(), scenario.baseUrl(), scenario.path(),
                authorizedToken);
        assertNotEquals(403, authorized.status(), scenario + ": " + authorized.body());
    }

    @Test
    void testEverySecuredControllerIsCoveredOrExplicitlyExcluded() throws Exception {
        Set<String> expected = new TreeSet<>();
        controllerScenarios().map(ControllerScenario::controller).forEach(expected::add);
        expected.addAll(AUTH_PLUGIN_CONTROLLERS);
        expected.addAll(ANONYMOUS_ONLY_CONTROLLERS);

        Path repositoryRoot = findRepositoryRoot();
        Set<String> actual = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(ModuleAuthorizationITCase::isControllerSource).toList()) {
                if (Files.readString(path).contains("@Secured")) {
                    String filename = path.getFileName().toString();
                    actual.add(filename.substring(0, filename.length() - ".java".length()));
                }
            }
        }
        assertEquals(actual, expected,
                "Every @Secured controller must have an authorization scenario or an explicit "
                        + "anonymous-only exclusion");
    }

    private static Stream<ControllerScenario> controllerScenarios() {
        return Stream.of(
                server("A2aAdminController", RequestMethod.GET,
                        "/v3/admin/ai/a2a/list?namespaceId=public&pageNo=1&pageSize=10", "r"),
                server("AgentAdminController", RequestMethod.GET,
                        "/v3/admin/ai/agents/list?namespaceId=public&pageNo=1&pageSize=10", "r"),
                server("AgentClientController", RequestMethod.GET,
                        "/v3/client/ai/agents/search?namespaceId=public&pageNo=1&pageSize=10", "r"),
                server("AgentSpecAdminController", RequestMethod.GET,
                        "/v3/admin/ai/agentspecs?namespaceId=public&agentSpecName=auth-it-missing",
                        "r"),
                server("AgentSpecClientController", RequestMethod.GET,
                        "/v3/client/ai/agentspecs/search?namespaceId=public&pageNo=1&pageSize=10",
                        "r"),
                server("AiResourceImportAdminController", RequestMethod.GET,
                        "/v3/admin/ai/import/sources", "r"),
                server("AiResourceSearchClientController", RequestMethod.GET,
                        "/v3/client/ai/resources/search?namespaceId=public&pageSize=10", "r"),
                server("McpAdminController", RequestMethod.GET,
                        "/v3/admin/ai/mcp/list?namespaceId=public&pageNo=1&pageSize=10", "r"),
                server("McpClientController", RequestMethod.GET,
                        "/v3/client/ai/mcp/search?namespaceId=public&pageNo=1&pageSize=10", "r"),
                server("PipelineAdminController", RequestMethod.GET,
                        "/v3/admin/ai/pipelines/list?resourceType=skill&pageNo=1&pageSize=10", "r"),
                server("PromptAdminController", RequestMethod.GET,
                        "/v3/admin/ai/prompt/list?namespaceId=public&pageNo=1&pageSize=10", "r"),
                server("PromptClientController", RequestMethod.GET,
                        "/v3/client/ai/prompt?namespaceId=public&promptKey=auth-it-missing", "r"),
                server("SkillAdminController", RequestMethod.GET,
                        "/v3/admin/ai/skills?namespaceId=public&skillName=auth-it-missing", "r"),

                console("ConsoleA2aController", RequestMethod.GET,
                        "/v3/console/ai/a2a/list?namespaceId=public&pageNo=1&pageSize=10", "r"),
                console("ConsoleAgentController", RequestMethod.GET,
                        "/v3/console/ai/agents/list?namespaceId=public&pageNo=1&pageSize=10", "r"),
                console("ConsoleAgentSpecController", RequestMethod.GET,
                        "/v3/console/ai/agentspecs?namespaceId=public&agentSpecName=auth-it-missing",
                        "r"),
                console("ConsoleAiResourceImportController", RequestMethod.GET,
                        "/v3/console/ai/import/sources", "r"),
                consoleAdmin("ConsoleCopilotConfigController", RequestMethod.GET,
                        "/v3/console/copilot/config", "r"),
                console("ConsoleCopilotController", RequestMethod.POST,
                        "/v3/console/copilot/skill/optimize", "w"),
                console("ConsoleMcpController", RequestMethod.GET,
                        "/v3/console/ai/mcp/list?namespaceId=public&pageNo=1&pageSize=10", "r"),
                console("ConsolePipelineController", RequestMethod.GET,
                        "/v3/console/ai/pipelines/list?resourceType=skill&pageNo=1&pageSize=10", "r"),
                console("ConsolePromptController", RequestMethod.GET,
                        "/v3/console/ai/prompt/list?namespaceId=public&pageNo=1&pageSize=10", "r"),
                console("ConsoleSkillController", RequestMethod.GET,
                        "/v3/console/ai/skills?namespaceId=public&skillName=auth-it-missing", "r"),
                console("ConsoleConfigController", RequestMethod.GET,
                        "/v3/console/cs/config/list?namespaceId=public&pageNo=1&pageSize=10", "r"),
                console("ConsoleHistoryController", RequestMethod.GET,
                        "/v3/console/cs/history/list?namespaceId=public"
                                + "&groupName=DEFAULT_GROUP&dataId=auth-it-missing"
                                + "&pageNo=1&pageSize=10", "r"),
                console("ConsoleClusterController", RequestMethod.GET,
                        "/v3/console/core/cluster/nodes", "r"),
                consoleAdmin("ConsoleNamespaceController", RequestMethod.GET,
                        "/v3/console/core/namespace?namespaceId=public", "r"),
                consoleAdmin("ConsolePluginController", RequestMethod.GET,
                        "/v3/console/plugin/list", "r"),
                console("ConsoleInstanceController", RequestMethod.GET,
                        "/v3/console/ns/instance/list?namespaceId=public"
                                + "&groupName=DEFAULT_GROUP&serviceName=auth-it-missing"
                                + "&pageNo=1&pageSize=10", "r"),
                console("ConsoleServiceController", RequestMethod.GET,
                        "/v3/console/ns/service/list?namespaceId=public&pageNo=1&pageSize=10", "r"),

                server("CapacityControllerV3", RequestMethod.GET,
                        "/v3/admin/cs/capacity?namespaceId=public", "r"),
                server("ConfigControllerV3", RequestMethod.GET,
                        "/v3/admin/cs/config/list?namespaceId=public&pageNo=1&pageSize=10", "r"),
                server("ConfigOpenApiController", RequestMethod.GET,
                        "/v3/client/cs/config?namespaceId=public"
                                + "&groupName=DEFAULT_GROUP&dataId=auth-it-missing", "r"),
                server("ConfigOpsControllerV3", RequestMethod.GET,
                        "/v3/admin/cs/ops/derby?sql=SELECT%201", "w"),
                server("HistoryControllerV3", RequestMethod.GET,
                        "/v3/admin/cs/history/list?namespaceId=public"
                                + "&groupName=DEFAULT_GROUP&dataId=auth-it-missing"
                                + "&pageNo=1&pageSize=10", "r"),
                server("ListenerControllerV3", RequestMethod.GET,
                        "/v3/admin/cs/listener?ip=127.0.0.1", "r"),
                server("MetricsControllerV3", RequestMethod.GET,
                        "/v3/admin/cs/metrics/cluster?ip=127.0.0.1", "r"),

                server("CoreOpsControllerV3", RequestMethod.GET,
                        "/v3/admin/core/ops/ids", "w"),
                server("NacosClusterControllerV3", RequestMethod.GET,
                        "/v3/admin/core/cluster/node/self", "r"),
                server("NamespaceControllerV3", RequestMethod.GET,
                        "/v3/admin/core/namespace/list", "r"),
                server("PluginControllerV3", RequestMethod.GET,
                        "/v3/admin/core/plugin/list", "r"),
                server("ServerLoaderControllerV3", RequestMethod.GET,
                        "/v3/admin/core/loader/current", "r"),

                server("ClientControllerV3", RequestMethod.GET,
                        "/v3/admin/ns/client/list", "r"),
                server("ClusterControllerV3", RequestMethod.PUT,
                        "/v3/admin/ns/cluster", "w"),
                server("HealthControllerV3", RequestMethod.GET,
                        "/v3/admin/ns/health/checkers", "w"),
                server("InstanceControllerV3", RequestMethod.GET,
                        "/v3/admin/ns/instance/list?namespaceId=public"
                                + "&groupName=DEFAULT_GROUP&serviceName=auth-it-missing", "r"),
                server("InstanceOpenApiController", RequestMethod.GET,
                        "/v3/client/ns/instance/list?namespaceId=public"
                                + "&groupName=DEFAULT_GROUP&serviceName=auth-it-missing", "r"),
                server("OperatorControllerV3", RequestMethod.GET,
                        "/v3/admin/ns/ops/switches", "r"),
                server("ServiceControllerV3", RequestMethod.GET,
                        "/v3/admin/ns/service/list?namespaceId=public&pageNo=1&pageSize=10", "r")
        );
    }

    private static ControllerScenario server(String controller, RequestMethod method, String path,
            String action) {
        return new ControllerScenario(controller, SERVER_BASE_URL, CONTEXT_PATH + path, method,
                action, false);
    }

    private static ControllerScenario console(String controller, RequestMethod method, String path,
            String action) {
        return new ControllerScenario(controller, CONSOLE_BASE_URL, path, method, action, false);
    }

    private static ControllerScenario consoleAdmin(String controller, RequestMethod method,
            String path, String action) {
        return new ControllerScenario(controller, CONSOLE_BASE_URL, path, method, action, true);
    }

    private static boolean isControllerSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/src/main/java/")
                && path.getFileName().toString().matches(".*Controller.*\\.java");
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("test/auth-test/pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate Nacos repository root");
    }

    private record ControllerScenario(String controller, String baseUrl, String path,
            RequestMethod method, String action, boolean globalAdminOnly) {

        private String identityPrefix() {
            String simpleName = controller.replace("ControllerV3", "")
                    .replace("Controller", "");
            return simpleName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
        }

        @Override
        public String toString() {
            return controller;
        }
    }
}
