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
import com.alibaba.nacos.api.ai.remote.request.AbstractAgentRequest;
import com.alibaba.nacos.api.ai.remote.request.AbstractMcpRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseAgentCardRequest;
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

class AiGrpcResourceParserTest {
    
    private AiGrpcResourceParser resourceParser;

    private static Stream<Arguments> fulContextRequests() {
        Arguments case1 = Arguments.of(mockMcpRequest("testNs", "testName"), "testNs", "testName",
                MockMcpRequest.class.getSimpleName());
        Arguments case2 = Arguments.of(mockAgentRequest("testNs", "testName"), "testNs", "testName",
                MockAgentRequest.class.getSimpleName());
        Arguments case3 = Arguments.of(makeReleaseAgentCardRequest("testNs", "testName", "testCardName"), "testNs", "testCardName",
                ReleaseAgentCardRequest.class.getSimpleName());
        Arguments case4 = Arguments.of(mockOtherRequest("testNs", "testName"),
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "", NotifySubscriberRequest.class.getSimpleName());
        return Stream.of(case1, case2, case3);
    }

    private static Stream<Arguments> withoutNamespaceRequests() {
        Arguments case1 = Arguments.of(mockMcpRequest("", "testName"),
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "testName", MockMcpRequest.class.getSimpleName());
        Arguments case2 = Arguments.of(mockAgentRequest(null, "testName"),
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "testName", MockAgentRequest.class.getSimpleName());
        Arguments case3 = Arguments.of(mockOtherRequest(null, "testName"),
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "", NotifySubscriberRequest.class.getSimpleName());
        return Stream.of(case1, case2, case3);
    }

    private static Stream<Arguments> withoutNameRequests() {
        Arguments case1 = Arguments.of(mockMcpRequest("testNs", ""), "testNs", "",
                MockMcpRequest.class.getSimpleName());
        Arguments case2 = Arguments.of(mockAgentRequest("testNs", null), "testNs", "",
                MockAgentRequest.class.getSimpleName());
        Arguments case3 = Arguments.of(mockOtherRequest("testNs", ""),
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "", NotifySubscriberRequest.class.getSimpleName());
        return Stream.of(case1, case2, case3);
    }
    
    @BeforeEach
    void setUp() throws Exception {
        resourceParser = new AiGrpcResourceParser();
    }

    @ParameterizedTest
    @MethodSource({"fulContextRequests", "withoutNamespaceRequests", "withoutNameRequests"})
    @Secured(signType = SignType.AI)
    void testParse(Request request, String expectedNamespaceId, String expectedName, String expectedRequestClassName) throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Resource actual = resourceParser.parse(request, secured);
        assertEquals(expectedNamespaceId, actual.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals(expectedName, actual.getName());
        assertEquals(SignType.AI, actual.getType());
        assertEquals(expectedRequestClassName, actual.getProperties()
                .getProperty(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.REQUEST_CLASS));
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

    private static ReleaseAgentCardRequest makeReleaseAgentCardRequest(String testNs, String agentName, String cardName) {
        ReleaseAgentCardRequest result = new ReleaseAgentCardRequest();
        result.setNamespaceId(testNs);
        result.setAgentName(agentName);
        AgentCard agentCard = new AgentCard();
        agentCard.setName(cardName);
        result.setAgentCard(agentCard);
        return result;
    }
    
    private static Request mockOtherRequest(String testNs, String testS) {
        NotifySubscriberRequest result = new NotifySubscriberRequest();
        result.setNamespace(testNs);
        result.setGroupName("");
        result.setServiceName(testS);
        return result;
    }

    @Secured(signType = SignType.AI)
    void forSecureAnnotationMethod() {

    }

    private Secured getMethodSecure() throws NoSuchMethodException {
        Method method = AiGrpcResourceParserTest.class.getDeclaredMethod("forSecureAnnotationMethod");
        return method.getAnnotation(Secured.class);
    }
    
    private static class MockMcpRequest extends AbstractMcpRequest {
    
    }

    private static class MockAgentRequest extends AbstractAgentRequest {

    }
}
