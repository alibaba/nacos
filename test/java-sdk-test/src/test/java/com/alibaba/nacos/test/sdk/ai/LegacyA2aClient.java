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

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.json.JsonUtils;

import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** One isolated 3.2.4 SDK JVM, controlled through public A2A operations only. */
final class LegacyA2aClient {
    private final Process process;
    private final BufferedReader replies;
    private final BufferedWriter requests;
    private final ExecutorService reader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "legacy-sdk-fixture-replies");
        thread.setDaemon(true);
        return thread;
    });
    private boolean closed;

    LegacyA2aClient(Properties properties) throws Exception {
        Path classes = Paths.get("target", "legacy-a2a-classes");
        Files.createDirectories(classes);
        Path classpath = Paths.get(System.getProperty("nacos.ai.compatibility.classpath-file",
            "../../target/ai-compatibility/legacy-classpath.txt"));
        String runtime = new String(Files.readAllBytes(classpath),
            StandardCharsets.UTF_8).trim();
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int compiled = ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics,
            "-proc:none", "-source", "8", "-target", "8", "-classpath", runtime,
            "-d", classes.toAbsolutePath().toString(), "src/test/compatibility/LegacyA2aProcess.java");
        if (compiled != 0) {
            throw new IllegalStateException(diagnostics.toString(StandardCharsets.UTF_8.name()));
        }
        Path output = Files.createTempFile(Paths.get("target", "failsafe-reports"),
            "legacy-a2a-", ".log");
        ProcessBuilder builder = new ProcessBuilder(Arrays.asList(
            Paths.get(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx256m",
            "-cp", classes.toAbsolutePath() + File.pathSeparator + runtime,
            "compatibility.LegacyA2aProcess"));
        StringWriter configuration = new StringWriter();
        properties.store(configuration, "Released SDK migration fixture");
        builder.environment().put("NACOS_LEGACY_PROPERTIES", Base64.getEncoder().encodeToString(
            configuration.toString().getBytes(StandardCharsets.UTF_8)));
        builder.redirectError(output.toFile());
        process = builder.start();
        replies = new BufferedReader(new InputStreamReader(process.getInputStream(),
            StandardCharsets.UTF_8));
        requests = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),
            StandardCharsets.UTF_8));
        try {
            Object source = readReply();
            if (source == null || !source.toString().contains("nacos-client-3.2.4.jar")) {
                throw new IllegalStateException("Migration fixture did not load the released SDK");
            }
        } catch (Exception error) {
            process.destroyForcibly();
            reader.shutdownNow();
            throw error;
        }
    }

    AgentCardDetailInfo getAgentCard(String name) throws NacosException {
        return getAgentCard(name, "", "");
    }

    AgentCardDetailInfo getAgentCard(String name, String version) throws NacosException {
        return getAgentCard(name, version, "");
    }

    AgentCardDetailInfo getAgentCard(String name, String version, String type) throws NacosException {
        Map<String, Object> request = request("get", name);
        request.put("version", version);
        request.put("registrationType", type);
        return JsonUtils.toObj(JsonUtils.toJson(invoke(request)), AgentCardDetailInfo.class);
    }

    void releaseAgentCard(AgentCard card, String type, boolean latest) throws NacosException {
        Map<String, Object> request = request("release", card.getName());
        request.put("card", card);
        request.put("registrationType", type);
        request.put("latest", latest);
        invoke(request);
    }

    void registerAgentEndpoint(String name, AgentEndpoint endpoint) throws NacosException {
        Map<String, Object> request = request("register", name);
        request.put("endpoint", endpoint);
        invoke(request);
    }

    void registerAgentEndpoint(String name, Collection<AgentEndpoint> endpoints) throws NacosException {
        Map<String, Object> request = request("batch", name);
        request.put("endpoints", endpoints);
        invoke(request);
    }

    void deregisterAgentEndpoint(String name, AgentEndpoint endpoint) throws NacosException {
        Map<String, Object> request = request("deregister", name);
        request.put("endpoint", endpoint);
        invoke(request);
    }

    synchronized void shutdown() throws NacosException {
        if (closed) {
            return;
        }
        try {
            invoke(request("shutdown", ""));
        } finally {
            closed = true;
            process.destroy();
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            reader.shutdownNow();
        }
    }

    private synchronized Object invoke(Map<String, Object> request) throws NacosException {
        try {
            requests.write(JsonUtils.toJson(request));
            requests.newLine();
            requests.flush();
            return readReply();
        } catch (NacosException error) {
            throw error;
        } catch (Exception error) {
            closed = true;
            process.destroyForcibly();
            reader.shutdownNow();
            throw new NacosException(NacosException.SERVER_ERROR, "Released SDK fixture failed", error);
        }
    }

    private Object readReply() throws Exception {
        String line = reader.submit(replies::readLine).get(90, TimeUnit.SECONDS);
        if (line == null) {
            throw new IllegalStateException("Released SDK process exited before replying");
        }
        Map<?, ?> result = JsonUtils.toObj(line, Map.class);
        int code = ((Number) result.get("code")).intValue();
        if (code != 0) {
            throw new NacosException(code, String.valueOf(result.get("message")));
        }
        return result.get("data");
    }

    private Map<String, Object> request(String operation, String name) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operation", operation);
        request.put("agentName", name);
        return request;
    }
}
