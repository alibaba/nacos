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
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Collections;
import java.util.Properties;

/** Application bytecode compiled only against the released 3.2.4 API. */
public final class LegacyAiApplication {

    private LegacyAiApplication() {
    }

    /** Verify inherited defaults still dispatch to old implementation overrides. */
    public static void verifyDefaults(AiService service) throws NacosException {
        require("mcp:null".equals(service.getMcpServer("mcp").getName()), "MCP default dispatch");
        require("agent::".equals(service.getAgentCard("agent").getName()), "A2A default dispatch");
        require("legacy-release".equals(service.releaseMcpServer(null, null)), "release overload dispatch");
    }

    /** Run real legacy wire operations with either the released or replacement SDK. */
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", args[0]);
        if (Boolean.parseBoolean(args[3])) {
            properties.setProperty("username", System.getenv("NACOS_TEST_AUTH_CLIENT_USERNAME"));
            properties.setProperty("password", System.getenv("NACOS_TEST_AUTH_CLIENT_PASSWORD"));
        }
        AiService service = AiFactory.createAiService(properties);
        try {
            verifyServer(service, args[1], args[2]);
            System.out.println("SDK_SOURCE=" + AiService.class.getProtectionDomain().getCodeSource().getLocation());
            System.out.println("LEGACY_AI_OK");
        } finally {
            service.shutdown();
        }
    }

    private static void verifyServer(AiService service, String mcpName, String agentName) throws Exception {
        McpServerBasicInfo mcp = new McpServerBasicInfo();
        mcp.setName(mcpName);
        mcp.setProtocol("stdio");
        mcp.setVersion("1.0.0");
        ServerVersionDetail version = new ServerVersionDetail();
        version.setVersion("1.0.0");
        mcp.setVersionDetail(version);
        String id = service.releaseMcpServer(mcp, new McpToolSpecification());
        require(id.equals(service.getMcpServer(mcpName, "1.0.0").getId()), "MCP legacy release/query");
        AbstractNacosMcpServerListener listener = new AbstractNacosMcpServerListener() {
            @Override
            public void onEvent(NacosMcpServerEvent event) {
            }
        };
        try {
            require(id.equals(service.subscribeMcpServer(mcpName, listener).getId()), "MCP legacy subscription");
        } finally {
            service.unsubscribeMcpServer(mcpName, listener);
        }
        AgentCard card = new AgentCard();
        card.setName(agentName);
        card.setVersion("1.0.0");
        card.setDescription("Legacy SDK compatibility");
        card.setCapabilities(new AgentCapabilities());
        AgentInterface agentInterface = new AgentInterface();
        agentInterface.setUrl("http://127.0.0.1:19001/agent");
        agentInterface.setProtocolBinding("JSONRPC");
        agentInterface.setProtocolVersion("0.3.0");
        card.setSupportedInterfaces(Collections.singletonList(agentInterface));
        service.releaseAgentCard(card);
        require(agentName.equals(service.getAgentCard(agentName).getName()), "A2A legacy release/query");
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(19001);
        endpoint.setVersion("1.0.0");
        endpoint.setTransport("JSONRPC");
        endpoint.setPath("/compat-runtime");
        endpoint.setProtocolVersion("0.3.0");
        try {
            service.registerAgentEndpoint(agentName, endpoint);
            boolean observed = false;
            for (int i = 0; i < 50; i++) {
                AgentCardDetailInfo detail = service.getAgentCard(agentName, "1.0.0");
                if ("http://127.0.0.1:19001/compat-runtime".equals(detail.getUrl())
                    || detail.getSupportedInterfaces() != null && detail.getSupportedInterfaces().stream()
                        .anyMatch(each -> "http://127.0.0.1:19001/compat-runtime".equals(each.getUrl()))) {
                    observed = true;
                    break;
                }
                Thread.sleep(100);
            }
            require(observed, "A2A legacy endpoint registration");
        } finally {
            service.deregisterAgentEndpoint(agentName, endpoint);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
