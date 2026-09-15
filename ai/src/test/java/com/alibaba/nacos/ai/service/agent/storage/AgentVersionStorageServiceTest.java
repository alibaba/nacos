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

package com.alibaba.nacos.ai.service.agent.storage;

import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.AiResourceStorageConsistencyMode;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorageChangeListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentVersionStorageServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String AGENT_NAME = "Nacos Agent";
    
    private static final String VERSION = "1.0.0-RC1";
    
    @Mock
    private AiResourceStorageRouter storageRouter;
    
    @Mock
    private AiResourceStorage storage;
    
    private AgentVersionStorageService service;
    
    @BeforeEach
    void setUp() {
        service = new AgentVersionStorageService(storageRouter,
            () -> NacosConfigAiResourceStorage.TYPE);
    }
    
    @Test
    void testPrepareBuildsDescriptorAndBytesWithoutAccessingStorage() {
        AgentVersionContent content = newContent();
        AgentVersionContentSerializer.SerializedContent expected =
            AgentVersionContentSerializer.serialize(content);
        
        PreparedAgentVersionWrite prepared = service.prepare(NAMESPACE_ID, AGENT_NAME, VERSION,
            content);
        
        AgentVersionStorageDescriptor descriptor = prepared.getDescriptor();
        assertArrayEquals(expected.getBytes(), prepared.getBytes());
        assertEquals(NacosConfigAiResourceStorage.TYPE, descriptor.getProvider());
        assertEquals("public:agent-version:agent__enc-Nacos-032Agent__1.0.0-RC1.json",
            descriptor.getKey());
        assertEquals(AgentVersionStorageDescriptor.NACOS_CONFIG_KEY_FORMAT,
            descriptor.getKeyFormat());
        assertEquals(AgentVersionStorageDescriptor.RAD_AGENT_NAME_CODEC,
            descriptor.getAgentNameCodec());
        assertEquals(expected.getContentDigest(), descriptor.getContentDigest());
        assertEquals((long) expected.getSize(), descriptor.getSize());
        verifyNoInteractions(storageRouter, storage);
    }
    
    @Test
    void testPrepareReplacementPreservesPersistedPointer() {
        AgentVersionStorageDescriptor current = service.prepare(NAMESPACE_ID, AGENT_NAME, VERSION,
            newContent()).getDescriptor();
        current.setProvider("object-store");
        current.setKey("opaque-existing-key");
        current.setKeyFormat(null);
        current.setAgentNameCodec(null);
        AgentVersionContent replacement = newContent();
        replacement.getCallInterfaces().get(0).setProtocolVersion("0.4");
        AgentVersionContentSerializer.SerializedContent expected =
            AgentVersionContentSerializer.serialize(replacement);
        
        PreparedAgentVersionWrite prepared = service.prepare(current, replacement);
        
        AgentVersionStorageDescriptor descriptor = prepared.getDescriptor();
        assertEquals("object-store", descriptor.getProvider());
        assertEquals("opaque-existing-key", descriptor.getKey());
        assertNull(descriptor.getKeyFormat());
        assertNull(descriptor.getAgentNameCodec());
        assertEquals(expected.getContentDigest(), descriptor.getContentDigest());
        assertEquals((long) expected.getSize(), descriptor.getSize());
        assertArrayEquals(expected.getBytes(), prepared.getBytes());
        verifyNoInteractions(storageRouter, storage);
    }
    
    @Test
    void testPrepareReplacementPreservesNacosConfigPointerMetadata() {
        AgentVersionStorageDescriptor current = service.prepare(NAMESPACE_ID, AGENT_NAME, VERSION,
            newContent()).getDescriptor();
        AgentVersionContent replacement = newContent();
        replacement.getCallInterfaces().get(0).setProtocolVersion("0.4");
        
        AgentVersionStorageDescriptor descriptor =
            service.prepare(current, replacement).getDescriptor();
        
        assertEquals(current.getProvider(), descriptor.getProvider());
        assertEquals(current.getKey(), descriptor.getKey());
        assertEquals(current.getKeyFormat(), descriptor.getKeyFormat());
        assertEquals(current.getAgentNameCodec(), descriptor.getAgentNameCodec());
        verifyNoInteractions(storageRouter, storage);
    }
    
    @Test
    void testPrepareReplacementRejectsInvalidDescriptorAndContent() {
        AgentVersionStorageDescriptor descriptor = service.prepare(NAMESPACE_ID, AGENT_NAME,
            VERSION, newContent()).getDescriptor();
        descriptor.setKey(null);
        
        assertThrows(IllegalArgumentException.class,
            () -> service.prepare(descriptor, newContent()));
        
        descriptor.setKey("opaque-key");
        assertThrows(IllegalArgumentException.class, () -> service.prepare(descriptor, null));
        verifyNoInteractions(storageRouter, storage);
    }
    
    @Test
    void testSavePreparedUsesProviderAndKeyCapturedDuringPrepare() throws NacosException {
        AtomicReference<String> configuredProvider = new AtomicReference<String>("object-store");
        service = new AgentVersionStorageService(storageRouter, configuredProvider::get);
        PreparedAgentVersionWrite prepared = service.prepare(NAMESPACE_ID, AGENT_NAME, VERSION,
            newContent());
        configuredProvider.set("other-store");
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        
        service.save(prepared);
        
        verify(storage).save(keyCaptor.capture(), any(byte[].class));
        assertEquals("object-store", keyCaptor.getValue().getProvider());
        assertEquals("public:agent-version:agent__enc-Nacos-032Agent__1.0.0-RC1.json",
            keyCaptor.getValue().getKey());
        assertEquals("object-store", prepared.getDescriptor().getProvider());
    }
    
    @Test
    void testPrepareSaveAndLoadRoundTrip() throws NacosException {
        PreparedAgentVersionWrite prepared = service.prepare(NAMESPACE_ID, AGENT_NAME, VERSION,
            newContent());
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class))).thenReturn(prepared.getBytes());
        
        service.save(prepared);
        AgentVersionContent loaded = service.load(prepared.getDescriptor());
        
        verify(storage).save(any(StorageKey.class), any(byte[].class));
        assertEquals("a2a", loaded.getCallInterfaces().get(0).getProtocol());
    }
    
    @Test
    void testSavePreparedRejectsNullBeforeRouting() {
        assertThrows(IllegalArgumentException.class,
            () -> service.save((PreparedAgentVersionWrite) null));
        verifyNoInteractions(storageRouter, storage);
    }
    
    @Test
    void testSaveUsesExactEncodedBytesAndReturnsDescriptor() throws NacosException {
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        AgentVersionContent content = newContent();
        AgentVersionContentSerializer.SerializedContent expected =
            AgentVersionContentSerializer.serialize(content);
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        
        AgentVersionStorageDescriptor descriptor = service.save(NAMESPACE_ID, AGENT_NAME, VERSION,
            content);
        
        verify(storage).save(keyCaptor.capture(), contentCaptor.capture());
        StorageKey key = keyCaptor.getValue();
        assertEquals(NacosConfigAiResourceStorage.TYPE, key.getProvider());
        assertEquals("public:agent-version:agent__enc-Nacos-032Agent__1.0.0-RC1.json",
            key.getKey());
        assertArrayEquals(expected.getBytes(), contentCaptor.getValue());
        assertEquals(key.getProvider(), descriptor.getProvider());
        assertEquals(key.getKey(), descriptor.getKey());
        assertEquals(AgentVersionStorageDescriptor.NACOS_CONFIG_KEY_FORMAT,
            descriptor.getKeyFormat());
        assertEquals(AgentVersionStorageDescriptor.RAD_AGENT_NAME_CODEC,
            descriptor.getAgentNameCodec());
        assertEquals(expected.getContentDigest(), descriptor.getContentDigest());
        assertEquals(AgentVersionStorageDescriptor.MEDIA_TYPE, descriptor.getMediaType());
        assertEquals(AgentVersionStorageDescriptor.SCHEMA_VERSION,
            descriptor.getSchemaVersion());
        assertEquals((long) expected.getSize(), descriptor.getSize());
    }
    
    @Test
    void testSaveUsesConfiguredCustomProvider() throws NacosException {
        service = new AgentVersionStorageService(storageRouter, () -> " object-store ");
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        
        AgentVersionStorageDescriptor descriptor = service.save(NAMESPACE_ID, AGENT_NAME, VERSION,
            newContent());
        
        assertEquals("object-store", descriptor.getProvider());
        assertNull(descriptor.getKeyFormat());
        assertNull(descriptor.getAgentNameCodec());
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        verify(storage).save(keyCaptor.capture(), any(byte[].class));
        assertEquals("object-store", keyCaptor.getValue().getProvider());
        assertEquals("public:agent-version:agent__enc-Nacos-032Agent__1.0.0-RC1.json",
            keyCaptor.getValue().getKey());
    }
    
    @Test
    void testSaveFallsBackToDefaultProviderWhenConfigurationIsBlank() throws NacosException {
        service = new AgentVersionStorageService(storageRouter, () -> "  ");
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        
        AgentVersionStorageDescriptor descriptor = service.save(NAMESPACE_ID, "agent", "1.0.0",
            newContent());
        
        assertEquals(NacosConfigAiResourceStorage.TYPE, descriptor.getProvider());
    }
    
    @Test
    void testSaveRejectsInvalidInputBeforeRouting() {
        assertThrows(IllegalArgumentException.class,
            () -> service.save("invalid namespace", AGENT_NAME, VERSION, newContent()));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(NAMESPACE_ID, "Agent代理", VERSION, newContent()));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(NAMESPACE_ID, AGENT_NAME, "v1", newContent()));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(NAMESPACE_ID, AGENT_NAME, VERSION, null));
        verify(storageRouter, never()).route(any(StorageKey.class));
    }
    
    @Test
    void testPrepareRejectsInvalidInputWithoutAccessingStorage() {
        assertThrows(IllegalArgumentException.class,
            () -> service.prepare("invalid namespace", AGENT_NAME, VERSION, newContent()));
        assertThrows(IllegalArgumentException.class,
            () -> service.prepare(NAMESPACE_ID, "Agent代理", VERSION, newContent()));
        assertThrows(IllegalArgumentException.class,
            () -> service.prepare(NAMESPACE_ID, AGENT_NAME, "v1", newContent()));
        assertThrows(IllegalArgumentException.class,
            () -> service.prepare(NAMESPACE_ID, AGENT_NAME, VERSION, null));
        verifyNoInteractions(storageRouter, storage);
    }
    
    @Test
    void testLoadValidatesRawBytesBeforeDecoding() throws NacosException {
        byte[] bytes = reorderedContentBytes();
        AgentVersionStorageDescriptor descriptor = descriptor(bytes);
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class))).thenReturn(bytes);
        
        AgentVersionContent result = service.load(descriptor);
        
        assertEquals(AgentVersionContent.KIND, result.getKind());
        assertEquals(AgentVersionContent.SCHEMA_VERSION, result.getSchemaVersion());
        assertEquals("a2a", result.getCallInterfaces().get(0).getProtocol());
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        verify(storage).get(keyCaptor.capture());
        assertEquals(descriptor.getProvider(), keyCaptor.getValue().getProvider());
        assertEquals(descriptor.getKey(), keyCaptor.getValue().getKey());
    }
    
    @Test
    void testLoadRejectsMissingContent() throws NacosException {
        byte[] bytes = reorderedContentBytes();
        AgentVersionStorageDescriptor descriptor = descriptor(bytes);
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class))).thenReturn(null);
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.load(descriptor));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
    }
    
    @Test
    void testLoadRejectsSizeMismatchBeforeDigest() throws NacosException {
        byte[] bytes = reorderedContentBytes();
        AgentVersionStorageDescriptor descriptor = descriptor(bytes);
        descriptor.setSize(descriptor.getSize() + 1);
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class))).thenReturn(bytes);
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.load(descriptor));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("Agent Version content size does not match its descriptor",
            exception.getErrMsg());
    }
    
    @Test
    void testLoadRejectsDigestMismatch() throws NacosException {
        byte[] bytes = reorderedContentBytes();
        AgentVersionStorageDescriptor descriptor = descriptor(bytes);
        descriptor.setContentDigest(AgentVersionContentSerializer.digest("other".getBytes(
            StandardCharsets.UTF_8)));
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class))).thenReturn(bytes);
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.load(descriptor));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("Agent Version content digest does not match its descriptor",
            exception.getErrMsg());
    }
    
    @Test
    void testLoadRejectsInvalidContentAfterIntegrityChecks() throws NacosException {
        byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
        AgentVersionStorageDescriptor descriptor = descriptor(bytes);
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class))).thenReturn(bytes);
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.load(descriptor));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("Agent Version content cannot be decoded", exception.getErrMsg());
    }
    
    @Test
    void testLoadRejectsInvalidDescriptorBeforeRouting() {
        AgentVersionStorageDescriptor descriptor = descriptor(reorderedContentBytes());
        descriptor.setKey(null);
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.load(descriptor));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        verify(storageRouter, never()).route(any(StorageKey.class));
    }
    
    @Test
    void testLoadWrapsMalformedPersistedKey() throws NacosException {
        AgentVersionStorageDescriptor descriptor = descriptor(reorderedContentBytes());
        descriptor.setKey("malformed");
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class)))
            .thenThrow(new IllegalArgumentException("malformed key"));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.load(descriptor));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("Invalid Agent Version storage key", exception.getErrMsg());
        assertNotNull(exception.getCause());
    }
    
    @Test
    void testDeleteUsesPersistedDescriptorKey() throws NacosException {
        AgentVersionStorageDescriptor descriptor = descriptor(reorderedContentBytes());
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        
        service.delete(descriptor);
        
        verify(storage).delete(keyCaptor.capture());
        assertEquals(descriptor.getProvider(), keyCaptor.getValue().getProvider());
        assertEquals(descriptor.getKey(), keyCaptor.getValue().getKey());
    }
    
    @Test
    void testConsistencyAndChangeListenersDelegateToStorageRouter() throws NacosException {
        AgentVersionStorageDescriptor descriptor = descriptor(reorderedContentBytes());
        AiResourceStorageChangeListener listener = event -> {
        };
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.consistencyMode()).thenReturn(AiResourceStorageConsistencyMode.STRONG);
        
        assertEquals(AiResourceStorageConsistencyMode.STRONG,
            service.consistencyMode(descriptor));
        service.addChangeListener(listener);
        service.removeChangeListener(listener);
        
        verify(storageRouter).addChangeListener(listener);
        verify(storageRouter).removeChangeListener(listener);
    }
    
    @Test
    void testDeleteWrapsMalformedPersistedKey() throws NacosException {
        AgentVersionStorageDescriptor descriptor = descriptor(reorderedContentBytes());
        descriptor.setKey("malformed");
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("malformed key"))
            .when(storage).delete(any(StorageKey.class));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.delete(descriptor));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("Invalid Agent Version storage key", exception.getErrMsg());
        assertNotNull(exception.getCause());
    }
    
    @Test
    void testUnavailableProviderIsReportedAsStorageFailure() {
        AgentVersionStorageDescriptor descriptor = descriptor(reorderedContentBytes());
        when(storageRouter.route(any(StorageKey.class)))
            .thenThrow(new IllegalStateException("missing"));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.load(descriptor));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertNotNull(exception.getCause());
    }
    
    @Test
    void testStorageExceptionIsPropagated() throws NacosException {
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        NacosException expected = new NacosException(NacosException.SERVER_ERROR, "save failed");
        org.mockito.Mockito.doThrow(expected).when(storage)
            .save(any(StorageKey.class), any(byte[].class));
        
        NacosException actual = assertThrows(NacosException.class,
            () -> service.save(NAMESPACE_ID, AGENT_NAME, VERSION, newContent()));
        
        assertEquals(expected, actual);
    }
    
    @Test
    void testSaveWrapsProviderIllegalArgumentException() throws NacosException {
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("provider rejected key"))
            .when(storage).save(any(StorageKey.class), any(byte[].class));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.save(NAMESPACE_ID, AGENT_NAME, VERSION, newContent()));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("Agent Version content cannot be saved", exception.getErrMsg());
        assertNotNull(exception.getCause());
    }
    
    @Test
    void testGetAndDeleteStorageExceptionsArePropagated() throws NacosException {
        AgentVersionStorageDescriptor descriptor = descriptor(reorderedContentBytes());
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
        NacosException getFailure = new NacosException(NacosException.SERVER_ERROR, "get failed");
        when(storage.get(any(StorageKey.class))).thenThrow(getFailure);
        
        assertEquals(getFailure, assertThrows(NacosException.class,
            () -> service.load(descriptor)));
        
        NacosException deleteFailure =
            new NacosException(NacosException.SERVER_ERROR, "delete failed");
        org.mockito.Mockito.doThrow(deleteFailure).when(storage).delete(any(StorageKey.class));
        assertEquals(deleteFailure, assertThrows(NacosException.class,
            () -> service.delete(descriptor)));
    }
    
    private AgentVersionStorageDescriptor descriptor(byte[] bytes) {
        AgentVersionStorageDescriptor result = new AgentVersionStorageDescriptor();
        result.setProvider(NacosConfigAiResourceStorage.TYPE);
        result.setKey("public:agent-version:agent__enc-Nacos-032Agent__1.0.0-RC1.json");
        result.setKeyFormat(AgentVersionStorageDescriptor.NACOS_CONFIG_KEY_FORMAT);
        result.setAgentNameCodec(AgentVersionStorageDescriptor.RAD_AGENT_NAME_CODEC);
        result.setContentDigest(AgentVersionContentSerializer.digest(bytes));
        result.setMediaType(AgentVersionStorageDescriptor.MEDIA_TYPE);
        result.setSchemaVersion(AgentVersionStorageDescriptor.SCHEMA_VERSION);
        result.setSize((long) bytes.length);
        return result;
    }
    
    private AgentVersionContent newContent() {
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor("descriptor");
        callInterface.setEndpointSourceOrder(Collections.singletonList(EndpointSource.RUNTIME));
        return new AgentVersionContent(Collections.singletonList(callInterface));
    }
    
    private byte[] reorderedContentBytes() {
        return ("{\"callInterfaces\":[{\"endpointSourceOrder\":[\"RUNTIME\"],"
            + "\"nativeDescriptor\":\"descriptor\",\"descriptorMediaType\":"
            + "\"application/json\",\"protocol\":\"a2a\"}],\"schemaVersion\":1,"
            + "\"kind\":\"AgentVersionContent\"}").getBytes(StandardCharsets.UTF_8);
    }
}
