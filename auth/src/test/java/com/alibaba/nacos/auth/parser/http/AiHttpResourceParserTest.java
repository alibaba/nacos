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

package com.alibaba.nacos.auth.parser.http;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiHttpResourceParserTest {
    
    @Mock
    private HttpServletRequest request;
    
    private AiHttpResourceParser resourceParser;
    
    @BeforeEach
    void setUp() {
        resourceParser = new AiHttpResourceParser();
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithNamespaceId() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/ai/mcp");
        when(request.getParameter(eq("mcpName"))).thenReturn("testMcp");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals("testMcp", actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithDefaultNamespace() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/ai/mcp");
        when(request.getParameter(eq("mcpName"))).thenReturn("testMcp");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, actual.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals("testMcp", actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithMcpPath() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/ai/mcp");
        when(request.getParameter(eq("mcpName"))).thenReturn("testMcp");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals("testMcp", actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithMcpPathWithoutName() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/ai/mcp");
        when(request.getParameter(eq("mcpName"))).thenReturn(null);
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(StringUtils.EMPTY, actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithA2aPathWithAgentName() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/ai/a2a");
        when(request.getParameter(eq("agentName"))).thenReturn("testAgent");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals("testAgent", actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithA2aPathWithAgentCard() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("agentCard", new String[] {"{\"name\":\"cardAgent\"}"});
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/ai/a2a");
        when(request.getParameter(eq("agentCard"))).thenReturn("{\"name\":\"cardAgent\"}");
        when(request.getParameterMap()).thenReturn(paramMap);
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals("cardAgent", actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithA2aPathWithInvalidAgentCard() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("agentCard", new String[] {"invalid-json"});
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/ai/a2a");
        when(request.getParameter(eq("agentCard"))).thenReturn("invalid-json");
        when(request.getParameterMap()).thenReturn(paramMap);
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(StringUtils.EMPTY, actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithSkillPath() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/v3/admin/ai/skills/list");
        when(request.getParameter(eq("skillName"))).thenReturn("my-skill");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals("my-skill", actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithSkillPathWithoutName() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/v3/admin/ai/skills/list");
        when(request.getParameter(eq("skillName"))).thenReturn(null);
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithPromptPath() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/v3/admin/ai/prompt/publish");
        when(request.getParameter(eq("promptKey"))).thenReturn("my-prompt");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals("my-prompt", actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithPromptPathWithoutKey() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/v3/admin/ai/prompt/list");
        when(request.getParameter(eq("promptKey"))).thenReturn(null);
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testParseWithUnknownPath() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/ai/unknown");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(StringUtils.EMPTY, actual.getName());
    }
    
    @Test
    @Secured(signType = "ai")
    void testGetProperties() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/ai/mcp");
        when(request.getParameter(eq("mcpName"))).thenReturn("testMcp");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(2, actual.getProperties().size());
        assertTrue(actual.getProperties().containsKey(
            com.alibaba.nacos.plugin.auth.constant.Constants.Resource.ACTION));
        assertEquals(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_MCP,
            actual.getProperties().getProperty(
                com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE));
    }
    
    @Test
    @Secured(signType = "ai")
    void testGetPropertiesForA2a() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/ai/a2a");
        when(request.getParameter(eq("agentName"))).thenReturn("testAgent");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_AGENT,
            actual.getProperties().getProperty(
                com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE));
    }
    
    @Test
    @Secured(signType = "ai")
    void testGetPropertiesForSkill() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/v3/admin/ai/skills/list");
        when(request.getParameter(eq("skillName"))).thenReturn("my-skill");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_SKILL,
            actual.getProperties().getProperty(
                com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE));
    }
    
    @Test
    @Secured(signType = "ai")
    void testGetPropertiesForPrompt() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/v3/admin/ai/prompt/publish");
        when(request.getParameter(eq("promptKey"))).thenReturn("my-prompt");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_PROMPT,
            actual.getProperties().getProperty(
                com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE));
    }
    
    @Test
    @Secured(signType = "ai")
    void testGetPropertiesForAgentSpec() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        when(request.getParameter(eq(Constants.NAMESPACE_ID))).thenReturn("testNs");
        when(request.getRequestURI()).thenReturn("/v3/admin/ai/agentSpec/list");
        when(request.getParameter(eq("agentSpecName"))).thenReturn("my-spec");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        
        Resource actual = resourceParser.parse(request, secured);
        
        assertEquals(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_AGENT_SPEC,
            actual.getProperties().getProperty(
                com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE));
    }
    
    private Secured getMethodSecure() throws NoSuchMethodException {
        StackTraceElement[] traces = new Exception().getStackTrace();
        StackTraceElement callerElement = traces[1];
        String methodName = callerElement.getMethodName();
        Method method = this.getClass().getDeclaredMethod(methodName);
        return method.getAnnotation(Secured.class);
    }
}
