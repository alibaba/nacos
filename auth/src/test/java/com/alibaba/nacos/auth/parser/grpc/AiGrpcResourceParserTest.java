/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.parser.grpc;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.remote.request.AbstractAgentRequest;
import com.alibaba.nacos.api.ai.remote.request.AbstractMcpRequest;
import com.alibaba.nacos.api.ai.remote.request.AbstractPromptRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointDeregisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRegisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSearchRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseAgentCardRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_AGENT;

class AiGrpcResourceParserTest {
    
    private AiGrpcResourceParser resourceParser;
    
    private static Stream<Arguments> fulContextRequests() {
        Arguments case1 = Arguments.of(mockMcpRequest("testNs", "testName"), "testNs", "testName",
            MockMcpRequest.class.getSimpleName());
        Arguments case2 = Arguments.of(mockAgentRequest("testNs", "testName"), "testNs", "testName",
            MockAgentRequest.class.getSimpleName());
        Arguments case3 = Arguments.of(
            makeReleaseAgentCardRequest("testNs", "testName", "testCardName"), "testNs",
            "testCardName", ReleaseAgentCardRequest.class.getSimpleName());
        Arguments case4 =
            Arguments.of(mockPromptRequest("testNs", "testPrompt"), "testNs", "testPrompt",
                MockPromptRequest.class.getSimpleName());
        Arguments case5 = Arguments.of(
            makeReleaseMcpServerRequest("testNs", "testName", "testSpecName"), "testNs",
            "testSpecName", ReleaseMcpServerRequest.class.getSimpleName());
        return Stream.of(case1, case2, case3, case4, case5);
    }
    
    private static Stream<Arguments> withoutNamespaceRequests() {
        Arguments case1 = Arguments.of(mockMcpRequest("", "testName"),
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "testName",
            MockMcpRequest.class.getSimpleName());
        Arguments case2 = Arguments.of(mockAgentRequest(null, "testName"),
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "testName",
            MockAgentRequest.class.getSimpleName());
        Arguments case3 = Arguments.of(mockPromptRequest(null, "testPrompt"),
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "testPrompt",
            MockPromptRequest.class.getSimpleName());
        Arguments case4 = Arguments.of(mockOtherRequest(null, "testName"),
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "",
            NotifySubscriberRequest.class.getSimpleName());
        return Stream.of(case1, case2, case3, case4);
    }
    
    private static Stream<Arguments> withoutNameRequests() {
        Arguments case1 = Arguments.of(mockMcpRequest("testNs", ""), "testNs", "",
            MockMcpRequest.class.getSimpleName());
        Arguments case2 = Arguments.of(mockAgentRequest("testNs", null), "testNs", "",
            MockAgentRequest.class.getSimpleName());
        Arguments case3 = Arguments.of(mockPromptRequest("testNs", ""), "testNs", "",
            MockPromptRequest.class.getSimpleName());
        Arguments case4 = Arguments.of(mockOtherRequest("testNs", ""),
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "",
            NotifySubscriberRequest.class.getSimpleName());
        return Stream.of(case1, case2, case3, case4);
    }
    
    private static Stream<Arguments> agentClientRequests() {
        return Stream.of(
            Arguments.of(agentSearchRpcRequest("search-ns"), "search-ns", ""),
            Arguments.of(new AgentSearchRpcRequest(),
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, ""),
            Arguments.of(agentDiscoveryRpcRequest("discovery-ns", "discovery-agent"),
                "discovery-ns", "discovery-agent"),
            Arguments.of(agentDiscoveryRpcRequest("discovery-ns", null), "discovery-ns", ""),
            Arguments.of(new AgentDiscoveryRpcRequest(),
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, ""),
            Arguments.of(agentEndpointRegisterRpcRequest("register-ns", "register-agent"),
                "register-ns", "register-agent"),
            Arguments.of(new AgentEndpointRegisterRpcRequest(),
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, ""),
            Arguments.of(agentEndpointDeregisterRpcRequest("deregister-ns", "deregister-agent"),
                "deregister-ns", "deregister-agent"),
            Arguments.of(new AgentEndpointDeregisterRpcRequest(),
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, ""));
    }
    
    @BeforeEach
    void setUp() throws Exception {
        resourceParser = new AiGrpcResourceParser();
    }
    
    @ParameterizedTest
    @MethodSource({"fulContextRequests", "withoutNamespaceRequests", "withoutNameRequests"})
    @Secured(signType = SignType.AI)
    void testParse(Request request, String expectedNamespaceId, String expectedName,
        String expectedRequestClassName)
        throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Resource actual = resourceParser.parse(request, secured);
        assertEquals(expectedNamespaceId, actual.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals(expectedName, actual.getName());
        assertEquals(SignType.AI, actual.getType());
        assertEquals(expectedRequestClassName, actual.getProperties()
            .getProperty(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.REQUEST_CLASS));
    }
    
    @ParameterizedTest
    @MethodSource("agentClientRequests")
    @Secured(signType = SignType.AI)
    void testParseAgentClientRequest(Request request, String expectedNamespaceId,
        String expectedName) throws NoSuchMethodException {
        Resource actual = resourceParser.parse(request, getMethodSecure());
        assertEquals(expectedNamespaceId, actual.getNamespaceId());
        assertEquals(expectedName, actual.getName());
        assertEquals(AI_TYPE_AGENT, actual.getProperties().getProperty(AI_TYPE));
    }
    
    private static AbstractMcpRequest mockMcpRequest(String testNs, String testS) {
        MockMcpRequest result = new MockMcpRequest();
        result.setNamespaceId(testNs);
        result.setMcpName(testS);
        return result;
    }
    
    private static MockAgentRequest mockAgentRequest(String testNs, String testS) {
        MockAgentRequest result = new MockAgentRequest();
        result.setNamespaceId(testNs);
        result.setAgentName(testS);
        return result;
    }
    
    private static ReleaseAgentCardRequest makeReleaseAgentCardRequest(String testNs,
        String agentName, String cardName) {
        ReleaseAgentCardRequest result = new ReleaseAgentCardRequest();
        result.setNamespaceId(testNs);
        result.setAgentName(agentName);
        AgentCard agentCard = new AgentCard();
        agentCard.setName(cardName);
        result.setAgentCard(agentCard);
        return result;
    }
    
    private static ReleaseMcpServerRequest makeReleaseMcpServerRequest(String testNs,
        String mcpName,
        String specName) {
        ReleaseMcpServerRequest result = new ReleaseMcpServerRequest();
        result.setNamespaceId(testNs);
        result.setMcpName(mcpName);
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        serverSpecification.setName(specName);
        result.setServerSpecification(serverSpecification);
        return result;
    }
    
    private static AbstractPromptRequest mockPromptRequest(String testNs, String promptKey) {
        MockPromptRequest result = new MockPromptRequest();
        result.setNamespaceId(testNs);
        result.setPromptKey(promptKey);
        return result;
    }
    
    private static Request mockOtherRequest(String testNs, String testS) {
        NotifySubscriberRequest result = new NotifySubscriberRequest();
        result.setNamespace(testNs);
        result.setGroupName("");
        result.setServiceName(testS);
        return result;
    }
    
    private static AgentSearchRpcRequest agentSearchRpcRequest(String namespaceId) {
        AgentSearchRequest search = new AgentSearchRequest();
        search.setNamespaceId(namespaceId);
        AgentSearchRpcRequest result = new AgentSearchRpcRequest();
        result.setSearchRequest(search);
        return result;
    }
    
    private static AgentDiscoveryRpcRequest agentDiscoveryRpcRequest(
        String namespaceId, String agentName) {
        AgentDiscoveryRequest discovery = new AgentDiscoveryRequest();
        discovery.setNamespaceId(namespaceId);
        if (agentName != null) {
            AgentReference reference = new AgentReference();
            reference.setAgentName(agentName);
            discovery.setReference(reference);
        }
        AgentDiscoveryRpcRequest result = new AgentDiscoveryRpcRequest();
        result.setDiscoveryRequest(discovery);
        return result;
    }
    
    private static AgentEndpointRegisterRpcRequest agentEndpointRegisterRpcRequest(
        String namespaceId,
        String agentName) {
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        batch.setNamespaceId(namespaceId);
        batch.setAgentName(agentName);
        AgentEndpointRegisterRpcRequest result = new AgentEndpointRegisterRpcRequest();
        result.setRegistrationBatch(batch);
        return result;
    }
    
    private static AgentEndpointDeregisterRpcRequest agentEndpointDeregisterRpcRequest(
        String namespaceId, String agentName) {
        AgentEndpointDeregisterRpcRequest result = new AgentEndpointDeregisterRpcRequest();
        result.setNamespaceId(namespaceId);
        result.setAgentName(agentName);
        return result;
    }
    
    @Secured(signType = SignType.AI)
    void forSecureAnnotationMethod() {
        
    }
    
    private Secured getMethodSecure() throws NoSuchMethodException {
        Method method =
            AiGrpcResourceParserTest.class.getDeclaredMethod("forSecureAnnotationMethod");
        return method.getAnnotation(Secured.class);
    }
    
    private static class MockMcpRequest extends AbstractMcpRequest {
        
    }
    
    private static class MockAgentRequest extends AbstractAgentRequest {
        
    }
    
    private static class MockPromptRequest extends AbstractPromptRequest {
        
    }
}
