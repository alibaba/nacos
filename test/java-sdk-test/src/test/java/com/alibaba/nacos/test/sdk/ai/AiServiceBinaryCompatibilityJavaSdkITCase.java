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

package com.alibaba.nacos.test.sdk.ai;

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Released 3.2.4 bytecode on the replacement SDK and isolated old/new SDK JVM smoke.
 *
 * <p>Scenario coverage: old third-party default dispatch, new optional accessors, MCP/A2A
 * release/query/subscription/Endpoint wire behavior and an optional disposable old server.
 * Enable with java-sdk-integration-test,ai-api-compatibility; see AI_API_COMPATIBILITY.md.</p>
 */
@EnabledIfSystemProperty(named = "nacos.ai.compatibility.enabled", matches = "true")
class AiServiceBinaryCompatibilityJavaSdkITCase extends JavaSdkBaseITCase {

    private static final Path LIBRARIES = Paths.get("target", "compatibility-libs");

    private static final Path CLASSES = Paths.get("target", "compatibility-classes");

    @BeforeAll
    static void compileAgainstReleasedApiOnly() throws Exception {
        Files.createDirectories(CLASSES);
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        assertNotNull(ToolProvider.getSystemJavaCompiler(), "Compatibility fixture needs a JDK");
        int result = ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics,
            "-proc:none", "-source", "8", "-target", "8", "-classpath",
            LIBRARIES.resolve("nacos-api-3.2.4.jar").toAbsolutePath().toString(),
            "-d", CLASSES.toAbsolutePath().toString(),
            "src/test/compatibility/LegacyThirdPartyAiService.java",
            "src/test/compatibility/LegacyAiApplication.java");
        assertEquals(0, result, diagnostics.toString(StandardCharsets.UTF_8.name()));
    }

    @Test
    void oldImplementationKeepsDefaultDispatchAndOptionalNewAccessors() throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {CLASSES.toUri().toURL()},
            AiService.class.getClassLoader())) {
            AiService oldImplementation = (AiService) loader.loadClass("compatibility.LegacyThirdPartyAiService")
                .getConstructor().newInstance();
            loader.loadClass("compatibility.LegacyAiApplication")
                .getMethod("verifyDefaults", AiService.class).invoke(null, oldImplementation);
            assertThrows(UnsupportedOperationException.class, oldImplementation::mcp);
            assertThrows(UnsupportedOperationException.class, oldImplementation::agent);
            assertThrows(UnsupportedOperationException.class, oldImplementation::skill);
            assertThrows(UnsupportedOperationException.class, oldImplementation::agentSpec);
            assertThrows(UnsupportedOperationException.class, oldImplementation::prompt);
            assertEquals("legacy-release", oldImplementation.releaseMcpServer(null, null, null, null, false));
            assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, assertThrows(NacosException.class,
                () -> oldImplementation.releaseMcpServer(null, null, null, null, true)).getErrCode());
        }
    }

    @Test
    void oldApplicationBytecodeRunsWithNewSdk() throws Exception {
        verifyOnCurrentServer(false);
    }

    @Test
    void releasedSdkRetainsOldWireOnNewServer() throws Exception {
        verifyOnCurrentServer(true);
    }

    @Test
    @EnabledIfSystemProperty(named = "nacos.ai.compatibility.old-server-address", matches = ".+")
    void newSdkRetainsOldWireOnDisposableOldServer() throws Exception {
        // The harness owns this isolated server and discards its database after the smoke.
        runApplication(false, System.getProperty("nacos.ai.compatibility.old-server-address"),
            randomServiceName("compat-old-mcp"), randomServiceName("compat-old-agent"), false);
    }

    private void verifyOnCurrentServer(boolean oldSdk) throws Exception {
        String mcp = randomServiceName("compat-mcp");
        String agent = randomServiceName("compat-agent");
        AiMaintainerService maintainer = AiMaintainerFactory.createAiMaintainerService(maintainerProperties());
        addCleanup(() -> maintainer.mcp().deleteMcpServer(mcp));
        addCleanup(() -> maintainer.a2a().deleteAgent(agent));
        runApplication(oldSdk, SERVER_ADDR, mcp, agent, AUTH_ENABLED);
    }

    private void runApplication(boolean oldSdk, String address, String mcp, String agent, boolean auth) throws Exception {
        String runtime = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        if (oldSdk) {
            // Resolve the released SDK's own dependency tree in a standalone fixture POM.
            runtime = new String(Files.readAllBytes(LIBRARIES.resolve("legacy-classpath.txt")),
                StandardCharsets.UTF_8).trim();
        }
        String classpath = CLASSES.toAbsolutePath() + File.pathSeparator + runtime;
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        if (!oldSdk) {
            command.add("-Dnacos.client.json.adapter="
                + System.getProperty("nacos.client.json.adapter", "auto"));
        }
        command.add("-cp");
        command.add(classpath);
        command.add("compatibility.LegacyAiApplication");
        command.add(address);
        command.add(mcp);
        command.add(agent);
        command.add(Boolean.toString(auth));
        Path output = Paths.get("target", "failsafe-reports", "legacy-" + mcp + ".log");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        if (auth) {
            Properties identity = sdkProperties();
            builder.environment().put("NACOS_TEST_AUTH_CLIENT_USERNAME", identity.getProperty(PropertyKeyConst.USERNAME));
            builder.environment().put("NACOS_TEST_AUTH_CLIENT_PASSWORD", identity.getProperty(PropertyKeyConst.PASSWORD));
        }
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(90, TimeUnit.SECONDS), "Legacy SDK process timed out: " + output);
            String text = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), text);
            assertTrue(text.contains("LEGACY_AI_OK"), text);
            assertTrue(text.contains(oldSdk ? "nacos-client-3.2.4.jar" : "3.3.0-SNAPSHOT.jar"), text);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }
}
