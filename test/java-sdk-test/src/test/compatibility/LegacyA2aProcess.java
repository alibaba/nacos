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

package compatibility;

import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Released-SDK process used by migration IT; calls only public AI methods. */
public final class LegacyA2aProcess {
    private LegacyA2aProcess() {
    }

    /** Execute one ordered stream of public A2A calls on one persistent released client. */
    public static void main(String[] args) throws Exception {
        PrintStream replies = System.out;
        System.setOut(System.err);
        ObjectMapper mapper = new ObjectMapper();
        Properties properties = new Properties();
        properties.load(new StringReader(new String(Base64.getDecoder().decode(
            System.getenv("NACOS_LEGACY_PROPERTIES")), StandardCharsets.UTF_8)));
        AiService service = AiFactory.createAiService(properties);
        try {
            reply(replies, mapper, 0, "ready", AiService.class.getProtectionDomain()
                .getCodeSource().getLocation().toString());
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in,
                StandardCharsets.UTF_8));
            String line;
            while ((line = input.readLine()) != null) {
                JsonNode request = mapper.readTree(line);
                String operation = request.path("operation").asText();
                if ("shutdown".equals(operation)) {
                    service.shutdown();
                    reply(replies, mapper, 0, "closed", null);
                    return;
                }
                try {
                    Object value = execute(service, mapper, request, operation);
                    reply(replies, mapper, 0, "success", value);
                } catch (NacosException error) {
                    reply(replies, mapper, error.getErrCode(), error.getErrMsg(), null);
                }
            }
        } finally {
            service.shutdown();
        }
    }

    private static Object execute(AiService service, ObjectMapper mapper, JsonNode request,
        String operation) throws Exception {
        String name = request.path("agentName").asText();
        switch (operation) {
            case "get":
                return service.getAgentCard(name, request.path("version").asText(""),
                    request.path("registrationType").asText(""));
            case "release":
                service.releaseAgentCard(mapper.treeToValue(request.get("card"), AgentCard.class),
                    request.path("registrationType").asText(), request.path("latest").asBoolean());
                return null;
            case "register":
                service.registerAgentEndpoint(name,
                    mapper.treeToValue(request.get("endpoint"), AgentEndpoint.class));
                return null;
            case "batch":
                List<AgentEndpoint> endpoints = new ArrayList<>();
                for (JsonNode endpoint : request.get("endpoints")) {
                    endpoints.add(mapper.treeToValue(endpoint, AgentEndpoint.class));
                }
                service.registerAgentEndpoint(name, endpoints);
                return null;
            case "deregister":
                service.deregisterAgentEndpoint(name,
                    mapper.treeToValue(request.get("endpoint"), AgentEndpoint.class));
                return null;
            default:
                throw new IllegalArgumentException("Unknown fixture operation: " + operation);
        }
    }

    private static void reply(PrintStream output, ObjectMapper mapper, int code,
        String message, Object data) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("message", message);
        result.put("data", data);
        output.println(mapper.writeValueAsString(result));
        output.flush();
    }
}
