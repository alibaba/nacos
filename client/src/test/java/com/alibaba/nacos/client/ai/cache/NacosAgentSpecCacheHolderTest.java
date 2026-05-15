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

package com.alibaba.nacos.client.ai.cache;

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NacosAgentSpecCacheHolderTest {
    
    private static final String SPEC_NAME = "test-agent";
    
    private static final String VERSION = "v1";
    
    private static final String NAMESPACE = "ns";
    
    @Mock
    private ConfigService configService;
    
    private NacosAgentSpecCacheHolder cacheHolder;
    
    @BeforeEach
    void setUp() {
        cacheHolder = new NacosAgentSpecCacheHolder(configService, NAMESPACE);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        cacheHolder.shutdown();
    }
    
    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    f.setAccessible(true);
                    return (T) f.get(target);
                }
            }
            c = c.getSuperclass();
        }
        throw new NoSuchFieldException(name);
    }
    
    private void mockIndex(String agentSpecName, String json) throws NacosException {
        String group = AgentSpecUtils.buildAgentSpecGroup(agentSpecName);
        when(configService.getConfig(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID), eq(group),
            anyLong())).thenReturn(json);
    }
    
    private void mockResource(String agentSpecName, String version, String filePath,
        String content) throws NacosException {
        String versionGroup = AgentSpecUtils.buildAgentSpecVersionGroup(agentSpecName, version);
        when(configService.getConfig(eq(filePath), eq(versionGroup), anyLong()))
            .thenReturn(content);
    }
    
    @Test
    void testQueryAgentSpecReturnsNullWhenIndexBlank() throws NacosException {
        mockIndex(SPEC_NAME, null);
        assertNull(cacheHolder.queryAgentSpec(SPEC_NAME));
    }
    
    @Test
    void testQueryAgentSpecReturnsNullWhenIndexInvalidJson() throws NacosException {
        mockIndex(SPEC_NAME, "not-json{");
        assertNull(cacheHolder.queryAgentSpec(SPEC_NAME));
    }
    
    @Test
    void testQueryAgentSpecReturnsNullWhenIndexHasNoFiles() throws NacosException {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[]}");
        assertNull(cacheHolder.queryAgentSpec(SPEC_NAME));
    }
    
    @Test
    void testQueryAgentSpecReturnsNullWhenIndexBlankVersion() throws NacosException {
        mockIndex(SPEC_NAME, "{\"version\":\"\",\"files\":[\"a\"]}");
        assertNull(cacheHolder.queryAgentSpec(SPEC_NAME));
    }
    
    @Test
    void testQueryAgentSpecBuildsAgentSpecFromManifest() throws NacosException {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"manifest.json\"]}");
        mockResource(SPEC_NAME, VERSION, "manifest.json",
            "{\"name\":\"manifest.json\",\"content\":\""
                + "{\\\"name\\\":\\\"my-agent\\\",\\\"description\\\":\\\"d\\\"}\"}");
        AgentSpec spec = cacheHolder.queryAgentSpec(SPEC_NAME);
        assertNotNull(spec);
        assertEquals(NAMESPACE, spec.getNamespaceId());
        assertEquals("my-agent", spec.getName());
        assertEquals("d", spec.getDescription());
    }
    
    @Test
    void testQueryAgentSpecSkipsBlankResourceContent() throws NacosException {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"manifest.json\",\"f.json\"]}");
        mockResource(SPEC_NAME, VERSION, "manifest.json",
            "{\"name\":\"manifest.json\",\"content\":\"{}\"}");
        mockResource(SPEC_NAME, VERSION, "f.json", "");
        AgentSpec spec = cacheHolder.queryAgentSpec(SPEC_NAME);
        assertNotNull(spec);
        assertEquals(0, spec.getResource().size());
    }
    
    @Test
    void testQueryAgentSpecSkipsBlankResourceContent2() throws NacosException {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"f.json\"]}");
        mockResource(SPEC_NAME, VERSION, "f.json", "   ");
        AgentSpec spec = cacheHolder.queryAgentSpec(SPEC_NAME);
        assertNotNull(spec);
        assertEquals(0, spec.getResource().size());
    }
    
    @Test
    void testQueryAgentSpecAddsNonManifestResource() throws NacosException {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"r1.json\"]}");
        mockResource(SPEC_NAME, VERSION, "r1.json",
            "{\"name\":\"toolA.txt\",\"type\":\"tool\",\"content\":\"x\"}");
        AgentSpec spec = cacheHolder.queryAgentSpec(SPEC_NAME);
        assertNotNull(spec);
        assertEquals(1, spec.getResource().size());
    }
    
    @Test
    void testQueryAgentSpecManifestWithMalformedContent() throws NacosException {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"manifest.json\"]}");
        // content is non-blank but not parseable as Map → caught and warned
        mockResource(SPEC_NAME, VERSION, "manifest.json",
            "{\"name\":\"manifest.json\",\"content\":\"not-json{\"}");
        AgentSpec spec = cacheHolder.queryAgentSpec(SPEC_NAME);
        assertNotNull(spec);
    }
    
    @Test
    void testSubscribeAgentSpecBlankNameThrows() {
        NacosException ex = assertThrows(NacosException.class,
            () -> cacheHolder.subscribeAgentSpec(""));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testSubscribeAgentSpecRegistersListenerAndCachesSpec() throws Exception {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"manifest.json\"]}");
        mockResource(SPEC_NAME, VERSION, "manifest.json",
            "{\"name\":\"manifest.json\",\"content\":\"{}\"}");
        AgentSpec spec = cacheHolder.subscribeAgentSpec(SPEC_NAME);
        assertNotNull(spec);
        // manifest listener registered
        verify(configService, times(1)).addListener(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), any(Listener.class));
        // resource listener registered
        verify(configService, times(1)).addListener(eq("manifest.json"),
            eq(AgentSpecUtils.buildAgentSpecVersionGroup(SPEC_NAME, VERSION)), any(Listener.class));
    }
    
    @Test
    void testSubscribeAgentSpecIdempotent() throws Exception {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"f.json\"]}");
        mockResource(SPEC_NAME, VERSION, "f.json",
            "{\"name\":\"r1\",\"type\":\"tool\",\"content\":\"x\"}");
        cacheHolder.subscribeAgentSpec(SPEC_NAME);
        // Second call should hit subscriptionMap.containsKey and return cached spec
        cacheHolder.subscribeAgentSpec(SPEC_NAME);
        verify(configService, times(1)).addListener(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), any(Listener.class));
    }
    
    @Test
    void testSubscribeAgentSpecHandlesNoIndex() throws Exception {
        mockIndex(SPEC_NAME, null);
        AgentSpec spec = cacheHolder.subscribeAgentSpec(SPEC_NAME);
        assertNull(spec);
        // Manifest listener is added even without index
        verify(configService, times(1)).addListener(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), any(Listener.class));
    }
    
    @Test
    void testUnsubscribeAgentSpecBlankNameNoOp() {
        cacheHolder.unsubscribeAgentSpec(null);
        verify(configService, never()).removeListener(anyString(), anyString(), any());
    }
    
    @Test
    void testUnsubscribeAgentSpecRemovesListeners() throws Exception {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"f.json\"]}");
        mockResource(SPEC_NAME, VERSION, "f.json",
            "{\"name\":\"r1\",\"type\":\"tool\",\"content\":\"x\"}");
        cacheHolder.subscribeAgentSpec(SPEC_NAME);
        cacheHolder.unsubscribeAgentSpec(SPEC_NAME);
        verify(configService, times(1)).removeListener(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), any(Listener.class));
        // resource listener also removed
        verify(configService, times(1)).removeListener(eq("f.json"),
            eq(AgentSpecUtils.buildAgentSpecVersionGroup(SPEC_NAME, VERSION)),
            any(Listener.class));
    }
    
    @Test
    void testUnsubscribeAgentSpecNotSubscribedNoOp() {
        cacheHolder.unsubscribeAgentSpec("nonexistent-spec");
        verify(configService, never()).removeListener(anyString(), anyString(), any());
    }
    
    @Test
    void testShutdownUnsubscribesAll() throws Exception {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[]}");
        cacheHolder.subscribeAgentSpec(SPEC_NAME);
        cacheHolder.shutdown();
        // After shutdown subscription map is cleared
        Map<String, ?> subs = readField(cacheHolder, "subscriptionMap");
        assertEquals(0, subs.size());
    }
    
    @Test
    void testManifestListenerVersionChangeReSubscribesResources() throws Exception {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"f1.json\"]}");
        mockResource(SPEC_NAME, VERSION, "f1.json",
            "{\"name\":\"r1\",\"type\":\"tool\",\"content\":\"x\"}");
        cacheHolder.subscribeAgentSpec(SPEC_NAME);
        
        // Capture the manifest listener
        ArgumentCaptor<Listener> listenerCaptor = ArgumentCaptor.forClass(Listener.class);
        verify(configService).addListener(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), listenerCaptor.capture());
        Listener manifestListener = listenerCaptor.getValue();
        
        // Switch to v2 with new file
        when(configService.getConfig(eq("f2.json"),
            eq(AgentSpecUtils.buildAgentSpecVersionGroup(SPEC_NAME, "v2")), anyLong()))
            .thenReturn("{\"name\":\"r2\",\"type\":\"tool\",\"content\":\"y\"}");
        when(configService.getConfig(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), anyLong()))
            .thenReturn("{\"version\":\"v2\",\"files\":[\"f2.json\"]}");
        
        manifestListener.receiveConfigInfo(
            "{\"version\":\"v2\",\"files\":[\"f2.json\"]}");
        // f1 listener removed, f2 listener added
        verify(configService, times(1)).removeListener(eq("f1.json"),
            eq(AgentSpecUtils.buildAgentSpecVersionGroup(SPEC_NAME, VERSION)),
            any(Listener.class));
        verify(configService, times(1)).addListener(eq("f2.json"),
            eq(AgentSpecUtils.buildAgentSpecVersionGroup(SPEC_NAME, "v2")),
            any(Listener.class));
    }
    
    @Test
    void testManifestListenerNoSubscriptionEarlyReturn() throws Exception {
        Method onManifestChanged = NacosAgentSpecCacheHolder.class.getDeclaredMethod(
            "onManifestChanged", String.class, String.class);
        onManifestChanged.setAccessible(true);
        // Should not throw and not interact with configService
        onManifestChanged.invoke(cacheHolder, "no-such-spec", "{}");
    }
    
    @Test
    void testParseAgentSpecIndexBlank() throws Exception {
        Method m = NacosAgentSpecCacheHolder.class.getDeclaredMethod("parseAgentSpecIndex",
            String.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, ""));
        assertNull(m.invoke(null, (String) null));
    }
    
    @Test
    void testParseAgentSpecIndexInvalidJson() throws Exception {
        Method m = NacosAgentSpecCacheHolder.class.getDeclaredMethod("parseAgentSpecIndex",
            String.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, "not-json{"));
    }
    
    @Test
    void testIsAgentSpecChangedNullOldReturnsTrue() throws Exception {
        Method m = NacosAgentSpecCacheHolder.class.getDeclaredMethod("isAgentSpecChanged",
            AgentSpec.class, AgentSpec.class);
        m.setAccessible(true);
        AgentSpec n = new AgentSpec();
        n.setName("a");
        assertTrue((boolean) m.invoke(cacheHolder, null, n));
    }
    
    @Test
    void testIsAgentSpecChangedSameReturnsFalse() throws Exception {
        Method m = NacosAgentSpecCacheHolder.class.getDeclaredMethod("isAgentSpecChanged",
            AgentSpec.class, AgentSpec.class);
        m.setAccessible(true);
        AgentSpec o = new AgentSpec();
        o.setName("a");
        AgentSpec n = new AgentSpec();
        n.setName("a");
        assertTrue(!(boolean) m.invoke(cacheHolder, o, n));
    }
    
    @Test
    void testIsAgentSpecChangedDifferentReturnsTrue() throws Exception {
        Method m = NacosAgentSpecCacheHolder.class.getDeclaredMethod("isAgentSpecChanged",
            AgentSpec.class, AgentSpec.class);
        m.setAccessible(true);
        AgentSpec o = new AgentSpec();
        o.setName("a");
        AgentSpec n = new AgentSpec();
        n.setName("b");
        assertTrue((boolean) m.invoke(cacheHolder, o, n));
    }
    
    @Test
    void testManifestListenerVersionGoesNull() throws Exception {
        // Set up subscription with v1 + a file
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"f1.json\"]}");
        mockResource(SPEC_NAME, VERSION, "f1.json",
            "{\"name\":\"r1\",\"type\":\"tool\",\"content\":\"x\"}");
        cacheHolder.subscribeAgentSpec(SPEC_NAME);
        // Now invoke onManifestChanged with empty index → version cleared, resources unsubscribed
        Method onManifestChanged = NacosAgentSpecCacheHolder.class.getDeclaredMethod(
            "onManifestChanged", String.class, String.class);
        onManifestChanged.setAccessible(true);
        onManifestChanged.invoke(cacheHolder, SPEC_NAME, "");
    }
    
    @Test
    void testManifestListenerExceptionPath() throws Exception {
        // First create a subscription so onManifestChanged finds a sub
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"f1.json\"]}");
        mockResource(SPEC_NAME, VERSION, "f1.json",
            "{\"name\":\"r1\",\"type\":\"tool\",\"content\":\"x\"}");
        cacheHolder.subscribeAgentSpec(SPEC_NAME);
        // Make configService throw on subsequent getConfig to push into the catch block
        when(configService.getConfig(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), anyLong()))
            .thenThrow(new NacosException(500, "boom"));
        Method onManifestChanged = NacosAgentSpecCacheHolder.class.getDeclaredMethod(
            "onManifestChanged", String.class, String.class);
        onManifestChanged.setAccessible(true);
        // Should not throw — Exception is caught
        onManifestChanged.invoke(cacheHolder, SPEC_NAME, "{\"version\":\"v2\",\"files\":[]}");
    }
    
    @Test
    void testReloadAndPublishRemovesWhenNewSpecIsNull() throws Exception {
        // Pre-cache a spec via subscribe + valid index
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"manifest.json\"]}");
        mockResource(SPEC_NAME, VERSION, "manifest.json",
            "{\"name\":\"manifest.json\",\"content\":\"{}\"}");
        cacheHolder.subscribeAgentSpec(SPEC_NAME);
        // Now invalidate index so loadAgentSpecFromConfig returns null
        when(configService.getConfig(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), anyLong()))
            .thenReturn("");
        Method reloadAndPublish = NacosAgentSpecCacheHolder.class.getDeclaredMethod(
            "reloadAndPublish", String.class);
        reloadAndPublish.setAccessible(true);
        reloadAndPublish.invoke(cacheHolder, SPEC_NAME);
    }
    
    @Test
    void testReloadAndPublishExceptionSwallowed() throws Exception {
        // configService returns a value but JacksonUtils throws on toObj path → exception via load
        when(configService.getConfig(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), anyLong()))
            .thenThrow(new NacosException(500, "fail"));
        Method reloadAndPublish = NacosAgentSpecCacheHolder.class.getDeclaredMethod(
            "reloadAndPublish", String.class);
        reloadAndPublish.setAccessible(true);
        // Should not throw (catch (Exception e))
        reloadAndPublish.invoke(cacheHolder, SPEC_NAME);
    }
    
    @Test
    void testSubscribeResourcesAddListenerThrowsSwallowed() throws Exception {
        // Mock addListener to throw NacosException for resource subscription
        when(configService.getConfig(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), anyLong()))
            .thenReturn("{\"version\":\"v1\",\"files\":[\"f.json\"]}");
        when(configService.getConfig(eq("f.json"),
            eq(AgentSpecUtils.buildAgentSpecVersionGroup(SPEC_NAME, VERSION)), anyLong()))
            .thenReturn("{\"name\":\"r1\",\"type\":\"tool\",\"content\":\"x\"}");
        org.mockito.Mockito.doThrow(new NacosException(500, "fail")).when(configService)
            .addListener(eq("f.json"),
                eq(AgentSpecUtils.buildAgentSpecVersionGroup(SPEC_NAME, VERSION)),
                any(Listener.class));
        // Should not throw — internal try/catch swallows
        cacheHolder.subscribeAgentSpec(SPEC_NAME);
    }
    
    @Test
    void testManifestListenerGetExecutorReturnsNull() throws Exception {
        mockIndex(SPEC_NAME, "{\"version\":\"v1\",\"files\":[\"f1.json\"]}");
        mockResource(SPEC_NAME, VERSION, "f1.json",
            "{\"name\":\"r1\",\"type\":\"tool\",\"content\":\"x\"}");
        cacheHolder.subscribeAgentSpec(SPEC_NAME);
        // Capture manifest listener and call its getExecutor + receiveConfigInfo
        org.mockito.ArgumentCaptor<Listener> captor =
            org.mockito.ArgumentCaptor.forClass(Listener.class);
        verify(configService).addListener(eq(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID),
            eq(AgentSpecUtils.buildAgentSpecGroup(SPEC_NAME)), captor.capture());
        Listener manifestListener = captor.getValue();
        assertNull(manifestListener.getExecutor());
        // Capture resource listener (file f1.json) and call its getExecutor + receive
        org.mockito.ArgumentCaptor<Listener> resCap =
            org.mockito.ArgumentCaptor.forClass(Listener.class);
        verify(configService).addListener(eq("f1.json"),
            eq(AgentSpecUtils.buildAgentSpecVersionGroup(SPEC_NAME, VERSION)), resCap.capture());
        Listener resourceListener = resCap.getValue();
        assertNull(resourceListener.getExecutor());
        // receiveConfigInfo on resource listener triggers onResourceChanged → reloadAndPublish
        resourceListener.receiveConfigInfo("{}");
    }
    
    @Test
    void testIsAgentSpecChangedNullOldLogsAndReturnsTrue() throws Exception {
        // Already covered by testIsAgentSpecChangedNullOldReturnsTrue but ensure newJson log path
        Method m = NacosAgentSpecCacheHolder.class.getDeclaredMethod("isAgentSpecChanged",
            AgentSpec.class, AgentSpec.class);
        m.setAccessible(true);
        // Pass null new and null old → newJson="null", returns true
        assertTrue((boolean) m.invoke(cacheHolder, null, null));
    }
    
    @Test
    void testAgentSpecIndexInnerGettersAndSetters() throws Exception {
        Class<?> indexClass = Class.forName(
            "com.alibaba.nacos.client.ai.cache.NacosAgentSpecCacheHolder$AgentSpecIndex");
        java.lang.reflect.Constructor<?> ctor = indexClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object idx = ctor.newInstance();
        Method setVersion = indexClass.getDeclaredMethod("setVersion", String.class);
        setVersion.setAccessible(true);
        setVersion.invoke(idx, "v1");
        Method getVersion = indexClass.getDeclaredMethod("getVersion");
        getVersion.setAccessible(true);
        assertEquals("v1", getVersion.invoke(idx));
        Method setFiles = indexClass.getDeclaredMethod("setFiles", java.util.List.class);
        setFiles.setAccessible(true);
        setFiles.invoke(idx, java.util.Collections.singletonList("a.json"));
        Method getFiles = indexClass.getDeclaredMethod("getFiles");
        getFiles.setAccessible(true);
        assertEquals(java.util.Collections.singletonList("a.json"), getFiles.invoke(idx));
    }
}
