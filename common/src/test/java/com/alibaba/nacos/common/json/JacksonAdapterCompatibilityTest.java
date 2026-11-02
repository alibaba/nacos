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

package com.alibaba.nacos.common.json;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.remote.request.ReleaseAgentCardRequest;
import com.alibaba.nacos.api.ai.remote.response.QueryAgentCardResponse;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRegisterRpcRequest;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.ai.utils.RadModelValidator;
import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapter;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapterNames;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonAdapterCompatibilityTest {
    
    @ParameterizedTest
    @ValueSource(strings = {"jackson2", "jackson3"})
    void testAgentCardSecurityRequirementsWireCompatibility(String adapterName) throws Exception {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, adapterName);
        resetJsonUtils();
        String requirements = "[{\"schemes\":{\"apiKey\":{\"list\":[]},"
            + "\"oauth2\":{\"list\":[\"read\",\"write\"]}}}]";
        String cardJson = "{\"securityRequirements\":" + requirements + "}";
        AgentCard card = JsonUtils.toObj(cardJson, AgentCard.class);
        assertEquals(2, card.getSecurityRequirements().get(0).size());
        assertEquals(card, JsonUtils.toObj(JsonUtils.toJsonBytes(card), AgentCard.class));
        String legacyJson = "{\"securityRequirements\":[{\"apiKey\":[],"
            + "\"oauth2\":[\"read\",\"write\"]}]}";
        AgentCard legacy = JsonUtils.toObj(legacyJson, AgentCard.class);
        assertEquals(card, legacy);
        assertEquals(JsonUtils.toObj(legacyJson, Map.class).get("securityRequirements"),
            JsonUtils.toObj(JsonUtils.toJson(legacy), Map.class).get("securityRequirements"));
        assertEquals(JsonUtils.toObj(cardJson, Map.class).get("securityRequirements"),
            JsonUtils.toObj(JsonUtils.toJson(card), Map.class).get("securityRequirements"));
        ReleaseAgentCardRequest release = JsonUtils.toObj("{\"agentCard\":" + cardJson + "}",
            ReleaseAgentCardRequest.class);
        assertEquals(card, release.getAgentCard());
        QueryAgentCardResponse response =
            JsonUtils.toObj("{\"agentCardDetailInfo\":" + cardJson + "}",
                QueryAgentCardResponse.class);
        assertEquals(card.getSecurityRequirements(),
            response.getAgentCardDetailInfo().getSecurityRequirements());
        assertThrows(NacosDeserializationException.class, () -> JsonUtils.toObj(
            "{\"securityRequirements\":[{\"schemes\":{\"apiKey\":\"invalid\"}}]}",
            AgentCard.class));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"jackson2", "jackson3"})
    void testAgentCardRequirementAlternativesAndCopies(String adapterName) throws Exception {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, adapterName);
        resetJsonUtils();
        String standard = "[{\"schemes\":{\"oauth2\":{\"list\":[\"read\",\"write\"]},"
            + "\"apiKey\":{\"list\":[]}}},{\"schemes\":{\"bearer\":{}}}]";
        String legacy = "[{\"oauth2\":[\"read\",\"write\"],\"apiKey\":[]},{\"bearer\":[]}]";
        for (String requirements : new String[] {standard, legacy}) {
            String json = "{\"securityRequirements\":" + requirements
                + ",\"security\":[{\"legacy\":[\"legacy-scope\"]}]}";
            AgentCard card = JsonUtils.toObj(json, AgentCard.class);
            assertEquals(2, card.getSecurityRequirements().size());
            assertEquals(Arrays.asList("read", "write"),
                card.getSecurityRequirements().get(0).get("oauth2"));
            assertEquals(Collections.emptyList(),
                card.getSecurityRequirements().get(0).get("apiKey"));
            assertEquals(Collections.emptyList(),
                card.getSecurityRequirements().get(1).get("bearer"));
            String encoded = JsonUtils.toJson(card);
            String expected = requirements.replace("\"bearer\":{}", "\"bearer\":{\"list\":[]}");
            assertEquals(JsonUtils.toObj(expected, List.class),
                jsonProperty(encoded, "securityRequirements"));
            assertEquals(jsonProperty(json, "security"), jsonProperty(encoded, "security"));
            assertEquals(card, JsonUtils.toObj(encoded, AgentCard.class));
            AgentCardDetailInfo detail = JsonUtils.toObj(json, AgentCardDetailInfo.class);
            assertEquals(card.getSecurityRequirements(), detail.getSecurityRequirements());
            assertEquals(jsonProperty(encoded, "securityRequirements"),
                jsonProperty(JsonUtils.toCanonicalJson(detail), "securityRequirements"));
        }
        AgentCard source = JsonUtils.toObj("{\"securityRequirements\":[{\"schemes\":{"
            + "\"oauth2\":{\"list\":[\"read\"]}}}]}", AgentCard.class);
        AgentCardDetailInfo target = new AgentCardDetailInfo();
        target.setSecurityRequirements(source.getSecurityRequirements());
        target.getSecurityRequirements().get(0).get("oauth2").add("write");
        assertEquals(JsonUtils.toObj("[{\"schemes\":{\"oauth2\":{\"list\":[\"read\",\"write\"]}}}]",
            List.class), jsonProperty(JsonUtils.toJson(target), "securityRequirements"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"jackson2", "jackson3"})
    void testAgentCardRequirementJavaAccessors(String adapterName) throws Exception {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, adapterName);
        resetJsonUtils();
        AgentCard card = new AgentCard();
        Map<String, List<String>> requirement = new HashMap<>();
        requirement.put("schemes", new ArrayList<>(Collections.singletonList("read")));
        card.setSecurityRequirements(new ArrayList<>(Collections.singletonList(requirement)));
        card.getSecurityRequirements().get(0).get("schemes").add("write");
        String encoded = JsonUtils.toJson(card);
        assertEquals(JsonUtils.toObj("[{\"schemes\":[\"read\",\"write\"]}]", List.class),
            jsonProperty(encoded, "securityRequirements"));
        assertEquals(Arrays.asList("read", "write"),
            JsonUtils.toObj(encoded, AgentCard.class).getSecurityRequirements().get(0)
                .get("schemes"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"jackson2", "jackson3"})
    void testAgentCardRequirementsEmptyAndMalformed(String adapterName) throws Exception {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, adapterName);
        resetJsonUtils();
        assertNull(JsonUtils.toObj("{}", AgentCard.class).getSecurityRequirements());
        assertNull(JsonUtils.toObj("{\"securityRequirements\":null}", AgentCard.class)
            .getSecurityRequirements());
        for (String requirements : new String[] {"[]", "[{}, {\"schemes\":{}}]"}) {
            AgentCard card = JsonUtils.toObj("{\"securityRequirements\":" + requirements + "}",
                AgentCard.class);
            assertEquals(JsonUtils.toObj(requirements, List.class),
                jsonProperty(JsonUtils.toJson(card), "securityRequirements"));
        }
        for (String invalid : new String[] {"[null]", "[1]", "{}", "[{\"schemes\":null}]",
            "[{\"schemes\":{\"apiKey\":[]}}]", "[{\"schemes\":{\"oauth2\":{\"list\":\"read\"}}}]",
            "[{\"schemes\":{\"oauth2\":{\"list\":[1]}}}]", "[{\"oauth2\":[null]}]",
            "[{\"schemes\":{},\"apiKey\":[]}]"}) {
            assertThrows(NacosDeserializationException.class, () -> JsonUtils.toObj(
                "{\"securityRequirements\":" + invalid + "}", AgentCard.class), invalid);
        }
    }
    
    private Object jsonProperty(String json, String property) {
        return JsonUtils.toObj(json, Map.class).get(property);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty(JsonUtils.ADAPTER_PROPERTY_NAME);
        resetJsonUtils();
    }
    
    @Test
    void testDefaultAdaptersAreRegisteredAndAvailable() {
        Map<String, NacosJsonAdapter> adapters = loadAdapters();
        
        assertTrue(adapters.containsKey(NacosJsonAdapterNames.JACKSON2));
        assertTrue(adapters.containsKey(NacosJsonAdapterNames.JACKSON3));
        assertTrue(adapters.get(NacosJsonAdapterNames.JACKSON2).isAvailable());
        assertTrue(adapters.get(NacosJsonAdapterNames.JACKSON3).isAvailable());
    }
    
    @Test
    void testAutoSelectsJackson3WhenBothAdaptersAreAvailable() {
        assertEquals(NacosJsonAdapterNames.JACKSON3, JsonUtils.selectedAdapterName());
        assertEquals("{\"name\":\"nacos\"}", JsonUtils.toJson(new SampleModel("nacos")));
    }
    
    @Test
    void testExplicitJackson2SelectionWorks() throws Exception {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON2);
        resetJsonUtils();
        
        assertEquals(NacosJsonAdapterNames.JACKSON2, JsonUtils.selectedAdapterName());
        assertEquals(new SampleModel("nacos"),
            JsonUtils.toObj("{\"name\":\"nacos\",\"unknown\":\"ignored\"}", SampleModel.class));
    }
    
    @Test
    void testExplicitJackson3SelectionWorks() throws Exception {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON3);
        resetJsonUtils();
        
        assertEquals(NacosJsonAdapterNames.JACKSON3, JsonUtils.selectedAdapterName());
        assertEquals(new SampleModel("nacos"),
            JsonUtils.toObj("{\"name\":\"nacos\",\"unknown\":\"ignored\"}", SampleModel.class));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"jackson2", "jackson3"})
    void testEndpointWireDefaultsAndExplicitNull(String adapterName) throws Exception {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, adapterName);
        resetJsonUtils();
        assertEquals(adapterName, JsonUtils.selectedAdapterName());
        String key = "\"uri\":\"https://example.com/rpc\",\"transport\":\"HTTP\"";
        Endpoint defaults = JsonUtils.toObj("{" + key + "}", Endpoint.class);
        assertEquals(0, defaults.getPriority());
        assertEquals(1D, defaults.getWeight());
        assertTrue(defaults.getHealthy());
        assertTrue(defaults.getEnabled());
        Endpoint disabled = JsonUtils.toObj("{" + key
            + ",\"enabled\":false,\"healthy\":false}", Endpoint.class);
        assertFalse(EndpointCanonicalizer.canonicalize(disabled).getEnabled());
        assertFalse(disabled.getHealthy());
        assertFalse(JsonUtils.toJson(disabled).contains("\"state\""));
        for (String field : new String[] {"priority", "weight", "healthy", "enabled"}) {
            String endpointJson = "{" + key + ",\"" + field + "\":null}";
            Endpoint invalid = JsonUtils.toObj(endpointJson, Endpoint.class);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EndpointCanonicalizer.canonicalize(invalid));
            assertTrue(error.getMessage().contains(field));
            String rpcJson = "{\"namespaceId\":\"public\",\"registrationBatch\":{"
                + "\"agentName\":\"demo\",\"protocol\":\"a2a\",\"runtimeVersion\":\"1.0.0\","
                + "\"endpoints\":[" + endpointJson + "]}}";
            AgentEndpointRegisterRpcRequest request = JsonUtils.toObj(rpcJson,
                AgentEndpointRegisterRpcRequest.class);
            assertThrows(IllegalArgumentException.class,
                () -> RadModelValidator.validate("public", request.getRegistrationBatch()));
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"auto", "jackson3"})
    void testJackson3OnlyClasspath(String configuredAdapter) throws Exception {
        withClasspath(false, true, configuredAdapter, loader -> {
            assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("com.fasterxml.jackson.core.JsonProcessingException"));
            assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper"));
            assertJsonRoundTrip(loader, NacosJsonAdapterNames.JACKSON3);
        });
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"auto", "jackson2"})
    void testJackson2OnlyClasspath(String configuredAdapter) throws Exception {
        withClasspath(true, false, configuredAdapter, loader -> {
            assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("tools.jackson.databind.ObjectMapper"));
            assertJsonRoundTrip(loader, NacosJsonAdapterNames.JACKSON2);
        });
    }
    
    @Test
    void testJackson2ProviderLoadsWithoutJackson2() throws Exception {
        withClasspath(false, true, "auto", loader -> {
            Class<?> providerClass = loader.loadClass(Jackson2JsonAdapter.class.getName());
            Object provider = providerClass.getConstructor().newInstance();
            assertEquals(false, providerClass.getMethod("isAvailable").invoke(provider));
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> providerClass.getMethod("toJson", Object.class).invoke(provider, "nacos"));
            assertInstanceOf(NacosLoadException.class, failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("Jackson 2 is not available"));
        });
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"jackson2", "jackson3"})
    void testMissingExplicitAdapterOnIsolatedClasspath(String selected) throws Exception {
        withClasspath(!"jackson2".equals(selected), !"jackson3".equals(selected), selected,
            loader -> assertUnavailable(loader,
                "Configured JSON adapter '" + selected + "' is not available"));
    }
    
    @Test
    void testNoJacksonOnIsolatedClasspath() throws Exception {
        withClasspath(false, false, "auto",
            loader -> assertUnavailable(loader, "No available JSON adapter"));
    }
    
    private void assertJsonRoundTrip(ClassLoader loader, String expected) throws Exception {
        Class<?> jsonUtils = loader.loadClass(JsonUtils.class.getName());
        String cardJson = "{\"securityRequirements\":[{\"schemes\":{\"apiKey\":{\"list\":[]}}}]}";
        Object card = jsonUtils.getMethod("toObj", String.class, Class.class)
            .invoke(null, cardJson, AgentCard.class);
        String encodedCard =
            (String) jsonUtils.getMethod("toJson", Object.class).invoke(null, card);
        assertTrue(encodedCard.contains("\"schemes\":{\"apiKey\":{\"list\":[]}}"));
        assertEquals(expected, jsonUtils.getMethod("selectedAdapterName").invoke(null));
        assertEquals("{\"issue\":15788}", jsonUtils.getMethod("toJson", Object.class)
            .invoke(null, Collections.singletonMap("issue", 15788)));
        Type type = new NacosTypeReference<Map<String, List<Integer>>>() {
        }.getType();
        assertEquals(Collections.singletonMap("items", Collections.singletonList(15788)),
            jsonUtils.getMethod("toObj", String.class, Type.class)
                .invoke(null, "{\"items\":[15788]}", type));
    }
    
    private void assertUnavailable(ClassLoader loader, String expected) throws Exception {
        Class<?> jsonUtils = loader.loadClass(JsonUtils.class.getName());
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
            () -> jsonUtils.getMethod("preload").invoke(null));
        assertInstanceOf(NacosLoadException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains(expected));
    }
    
    private void withClasspath(boolean jackson2, boolean jackson3, String configured,
        ClasspathAssertion assertion) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        URL[] locations =
            {Jackson2JsonAdapter.class.getProtectionDomain().getCodeSource().getLocation(),
                JsonUtils.class.getProtectionDomain().getCodeSource().getLocation()};
        try (URLClassLoader loader = new URLClassLoader(locations, original) {
            
            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
                if ((!jackson2 && (name.startsWith("com.fasterxml.jackson.core.")
                    || name.startsWith("com.fasterxml.jackson.databind.")))
                    || (!jackson3 && name.startsWith("tools.jackson."))) {
                    throw new ClassNotFoundException(name);
                }
                if (name.startsWith("com.alibaba.nacos.common.json.")
                    || name.startsWith("com.alibaba.nacos.api.utils.json.")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
            
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals("META-INF/services/" + NacosJsonAdapter.class.getName())) {
                    return findResources(name);
                }
                return super.getResources(name);
            }
        }) {
            System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, configured);
            Thread.currentThread().setContextClassLoader(loader);
            assertion.check(loader);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
    
    private interface ClasspathAssertion {
        
        void check(ClassLoader loader) throws Exception;
    }
    
    private Map<String, NacosJsonAdapter> loadAdapters() {
        Map<String, NacosJsonAdapter> result = new HashMap<String, NacosJsonAdapter>();
        for (NacosJsonAdapter adapter : ServiceLoader.load(NacosJsonAdapter.class)) {
            result.put(adapter.name(), adapter);
        }
        return result;
    }
    
    private void resetJsonUtils() throws Exception {
        Method resetMethod = JsonUtils.class.getDeclaredMethod("resetForTest");
        resetMethod.setAccessible(true);
        resetMethod.invoke(null);
    }
    
    public static class SampleModel {
        
        private String name;
        
        public SampleModel() {
        }
        
        SampleModel(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SampleModel)) {
                return false;
            }
            SampleModel that = (SampleModel) o;
            return String.valueOf(name).equals(String.valueOf(that.name));
        }
        
        @Override
        public int hashCode() {
            return name == null ? 0 : name.hashCode();
        }
    }
}
