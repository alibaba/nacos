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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Authorization scenarios for every non-anonymous secured Nacos HTTP controller.
 *
 * <p>Each scenario verifies missing identity, invalid identity, a shared valid identity without
 * authority, and the global administrator. Fine-grained grants and resource parsing are covered
 * by {@link ResourceAuthorizationITCase}; reusing prepared identities here keeps the exhaustive
 * controller matrix deterministic and fast enough for the consolidated CI suite.</p>
 *
 * @author Nacos
 */
public class ModuleAuthorizationITCase extends AuthITCase {

    private static final int EXPECTED_SECURED_OPERATION_COUNT = 390;

    private static final String OPERATION_COVERAGE_MANIFEST =
            "test/openapi-test/AUTHORIZATION_OPERATION_COVERAGE.md";

    private static final Pattern SECURED_ANNOTATION = Pattern.compile(
            "@Secured(?:\\s*\\((.*?)\\))?", Pattern.DOTALL);

    private static final Pattern FOLLOWING_PUBLIC_METHOD = Pattern.compile(
            "\\bpublic\\s+(?:static\\s+)?(?:final\\s+)?[^;={}]*?"
                    + "\\b([A-Za-z_$][\\w$]*)\\s*\\(", Pattern.DOTALL);

    private static final Map<String, Integer> EXPECTED_SECURED_OPERATIONS = Map.ofEntries(
            Map.entry("A2aAdminController", 6),
            Map.entry("AgentAdminController", 17),
            Map.entry("AgentClientController", 7),
            Map.entry("AgentSpecAdminController", 18),
            Map.entry("AgentSpecClientController", 2),
            Map.entry("AiResourceImportAdminController", 4),
            Map.entry("AiResourceSearchClientController", 1),
            Map.entry("ArdSearchController", 5),
            Map.entry("ArdWellKnownController", 1),
            Map.entry("CapacityControllerV3", 2),
            Map.entry("ClientControllerV3", 7),
            Map.entry("ClusterControllerV3", 1),
            Map.entry("ConfigControllerV3", 15),
            Map.entry("ConfigOpenApiController", 1),
            Map.entry("ConfigOpsControllerV3", 4),
            Map.entry("ConsoleA2aController", 6),
            Map.entry("ConsoleAgentController", 17),
            Map.entry("ConsoleAgentSpecController", 17),
            Map.entry("ConsoleAiResourceImportController", 4),
            Map.entry("ConsoleClusterController", 1),
            Map.entry("ConsoleConfigController", 13),
            Map.entry("ConsoleCopilotConfigController", 2),
            Map.entry("ConsoleCopilotController", 4),
            Map.entry("ConsoleHistoryController", 4),
            Map.entry("ConsoleInstanceController", 3),
            Map.entry("ConsoleMcpController", 22),
            Map.entry("ConsoleNamespaceController", 6),
            Map.entry("ConsolePipelineController", 4),
            Map.entry("ConsolePluginController", 5),
            Map.entry("ConsolePromptController", 18),
            Map.entry("ConsoleServiceController", 8),
            Map.entry("ConsoleSkillController", 20),
            Map.entry("CoreOpsControllerV3", 3),
            Map.entry("HealthControllerV3", 2),
            Map.entry("HistoryControllerV3", 4),
            Map.entry("InstanceControllerV3", 8),
            Map.entry("InstanceOpenApiController", 3),
            Map.entry("ListenerControllerV3", 1),
            Map.entry("McpAdminController", 19),
            Map.entry("McpClientController", 6),
            Map.entry("MetricsControllerV3", 2),
            Map.entry("NacosClusterControllerV3", 4),
            Map.entry("NamespaceControllerV3", 6),
            Map.entry("OperatorControllerV3", 4),
            Map.entry("PermissionControllerV3", 4),
            Map.entry("PipelineAdminController", 4),
            Map.entry("PluginControllerV3", 4),
            Map.entry("PromptAdminController", 24),
            Map.entry("PromptClientController", 2),
            Map.entry("RoleControllerV3", 4),
            Map.entry("ServerLoaderControllerV3", 5),
            Map.entry("ServiceControllerV3", 7),
            Map.entry("SkillAdminController", 20),
            Map.entry("SkillClientController", 2),
            Map.entry("UserControllerV3", 5),
            Map.entry("VisibilityGrantControllerV3", 2));

    private static final Set<String> AUTH_PLUGIN_CONTROLLERS = Set.of(
            "PermissionControllerV3", "RoleControllerV3", "UserControllerV3",
            "VisibilityGrantControllerV3");

    private static final Set<String> ANONYMOUS_ONLY_CONTROLLERS = Set.of(
            "ArdSearchController", "ArdWellKnownController");

    @ParameterizedTest(name = "{0}")
    @MethodSource("controllerScenarios")
    void testControllerAuthorization(ControllerScenario scenario) throws Exception {
        assertDenied(request(scenario.method(), scenario.baseUrl(), scenario.path(), null));
        assertDenied(request(scenario.method(), scenario.baseUrl(), scenario.path(),
                "invalid-token"));

        assertDenied(request(scenario.method(), scenario.baseUrl(), scenario.path(),
                noPermissionToken()));

        Response authorized = request(scenario.method(), scenario.baseUrl(), scenario.path(),
                adminToken());
        assertNotEquals(403, authorized.status(), scenario + ": " + authorized.body());
    }

    @Test
    void testEverySecuredOperationIsCoveredOrExplicitlyClassified() throws Exception {
        Set<String> expected = new TreeSet<>();
        controllerScenarios().map(ControllerScenario::controller).forEach(expected::add);
        expected.addAll(AUTH_PLUGIN_CONTROLLERS);
        expected.addAll(ANONYMOUS_ONLY_CONTROLLERS);

        Path repositoryRoot = findRepositoryRoot();
        Map<String, Integer> actual = new TreeMap<>();
        Map<String, String> actualOperations = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(ModuleAuthorizationITCase::isControllerSource).toList()) {
                long securedOperations = Files.readAllLines(path).stream()
                        .map(String::stripLeading).filter(line -> line.startsWith("@Secured"))
                        .count();
                if (securedOperations > 0) {
                    String filename = path.getFileName().toString();
                    String controller = filename.substring(0,
                            filename.length() - ".java".length());
                    actual.put(controller, Math.toIntExact(securedOperations));
                    actualOperations.putAll(readSecuredOperations(path));
                }
            }
        }
        assertEquals(expected, actual.keySet(),
                "Every @Secured operation must belong to a directly tested controller, an "
                        + "authorization-equivalent controller group, or an explicit "
                        + "conditional-anonymous classification");
        assertEquals(EXPECTED_SECURED_OPERATIONS, actual,
                "The operation-level inventory changed; classify every added, removed, or "
                        + "re-annotated @Secured method before updating the registry");
        assertEquals(EXPECTED_SECURED_OPERATION_COUNT,
                actual.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(readManifestOperations(repositoryRoot), actualOperations,
                "Every @Secured method and normalized authorization tuple must match the "
                        + "operation-level coverage manifest");
    }

    private static Map<String, String> readSecuredOperations(Path source) throws Exception {
        String content = Files.readString(source);
        Matcher securedMatcher = SECURED_ANNOTATION.matcher(content);
        Map<String, String> result = new HashMap<>();
        while (securedMatcher.find()) {
            Matcher methodMatcher = FOLLOWING_PUBLIC_METHOD.matcher(content);
            methodMatcher.region(securedMatcher.end(), content.length());
            assertTrue(methodMatcher.find(), "No public method follows @Secured in " + source);
            String operation = source.getFileName().toString().replace(".java", "") + '#'
                    + methodMatcher.group(1);
            String tuple = normalizeWhitespace(securedMatcher.group(1));
            assertNull(result.put(operation, tuple), "Duplicate secured operation: " + operation);
        }
        return result;
    }

    private static Map<String, String> readManifestOperations(Path repositoryRoot)
            throws Exception {
        Path manifest = repositoryRoot.resolve(OPERATION_COVERAGE_MANIFEST);
        Map<String, String> result = new TreeMap<>();
        for (String line : Files.readAllLines(manifest)) {
            if (!line.startsWith("| `")) {
                continue;
            }
            String[] cells = line.split("\\|", -1);
            assertTrue(cells.length >= 7, "Malformed operation coverage row: " + line);
            String operation = stripCode(cells[2]);
            String tuple = stripCode(cells[3]);
            String parser = stripCode(cells[4]);
            String coverage = cells[5].trim();
            assertFalse(parser.isBlank(), "Missing parser classification: " + operation);
            assertFalse(coverage.isBlank(), "Missing test attribution: " + operation);
            if (tuple.contains("ALLOW_ANONYMOUS")) {
                assertTrue(coverage.contains("ConditionalAnonymousAuthorizationITCase"),
                        "Conditional anonymous operation needs direct coverage: " + operation);
            }
            if (tuple.contains("ONLY_IDENTITY")) {
                assertTrue(coverage.contains("IdentityOnlyAuthorizationITCase")
                                || coverage.contains("DefaultAuthApiITCase")
                                || coverage.contains("ResourceAuthorizationITCase"),
                        "Identity-only operation needs direct coverage: " + operation);
            }
            assertNull(result.put(operation, tuple),
                    "Duplicate operation coverage row: " + operation);
        }
        assertEquals(EXPECTED_SECURED_OPERATION_COUNT, result.size(),
                "Operation manifest must classify every @Secured method");
        return result;
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String stripCode(String cell) {
        String value = cell.trim();
        assertTrue(value.length() >= 2 && value.startsWith("`") && value.endsWith("`"),
                "Manifest value must use inline code: " + cell);
        return value.substring(1, value.length() - 1);
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
                server("SkillClientController", RequestMethod.GET,
                        "/v3/client/ai/skills/search?namespaceId=public&pageNo=1&pageSize=10", "r"),

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
            if (Files.isRegularFile(current.resolve("test/openapi-test/pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate Nacos repository root");
    }

    private record ControllerScenario(String controller, String baseUrl, String path,
            RequestMethod method, String action, boolean globalAdminOnly) {

        @Override
        public String toString() {
            return controller;
        }
    }
}
