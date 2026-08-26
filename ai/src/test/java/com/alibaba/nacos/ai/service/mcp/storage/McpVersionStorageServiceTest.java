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

package com.alibaba.nacos.ai.service.mcp.storage;

import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpVersionStorageServiceTest {
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    private static final byte[] SERVER = "server".getBytes(StandardCharsets.UTF_8);
    
    private static final byte[] TOOLS = "tools".getBytes(StandardCharsets.UTF_8);
    
    private static final byte[] RESOURCES = "resources".getBytes(StandardCharsets.UTF_8);
    
    @Mock
    private AiResourceStorageRouter storageRouter;
    
    @Mock
    private AiResourceStorage storage;
    
    private McpVersionStorageService service;
    
    @BeforeEach
    void setUp() {
        service = new McpVersionStorageService(storageRouter);
    }
    
    @Test
    void testDefaultConstructorIsAvailableForSpring() {
        assertNotNull(new McpVersionStorageService());
    }
    
    @Test
    void testSaveWritesToolsResourcesThenServer() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor descriptor = descriptor(true, true);
        
        service.save(descriptor, contents(true, true));
        
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(storage, times(3)).save(keyCaptor.capture(), contentCaptor.capture());
        assertEquals(List.of(descriptor.getToolKey(), descriptor.getResourceKey(),
            descriptor.getServerKey()), keys(keyCaptor));
        assertArrayEquals(TOOLS, contentCaptor.getAllValues().get(0));
        assertArrayEquals(RESOURCES, contentCaptor.getAllValues().get(1));
        assertArrayEquals(SERVER, contentCaptor.getAllValues().get(2));
    }
    
    @Test
    void testSaveSupportsMissingOptionalFiles() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor descriptor = descriptor(false, false);
        service.save(descriptor, contents(false, false));
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        verify(storage).save(keyCaptor.capture(), any(byte[].class));
        assertEquals(descriptor.getServerKey(), keyCaptor.getValue().getKey());
    }
    
    @Test
    void testSaveStopsAfterPartialFailureAndLeavesRetryableContent() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor descriptor = descriptor(true, true);
        NacosException failure = new NacosException(NacosException.SERVER_ERROR, "resource failed");
        doAnswer(invocation -> {
            StorageKey key = invocation.getArgument(0);
            if (descriptor.getResourceKey().equals(key.getKey())) {
                throw failure;
            }
            return null;
        }).when(storage).save(any(StorageKey.class), any(byte[].class));
        
        assertEquals(failure,
            assertThrows(NacosException.class,
                () -> service.save(descriptor, contents(true, true))));
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        verify(storage, times(2)).save(keyCaptor.capture(), any(byte[].class));
        assertEquals(List.of(descriptor.getToolKey(), descriptor.getResourceKey()),
            keys(keyCaptor));
    }
    
    @Test
    void testSaveValidatesDescriptorAndContentPresenceBeforeRouting() {
        McpVersionStorageDescriptor descriptor = descriptor(true, false);
        assertThrows(IllegalArgumentException.class, () -> service.save(descriptor, null));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(descriptor, contents(false, false)));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(descriptor, contents(true, true)));
        descriptor.setProvider("other");
        assertThrows(IllegalArgumentException.class,
            () -> service.save(descriptor, contents(true, false)));
        verifyNoInteractions(storageRouter, storage);
    }
    
    @Test
    void testSaveWrapsProviderKeyRejection() throws Exception {
        givenStorage();
        doThrow(new IllegalArgumentException("bad key")).when(storage)
            .save(any(StorageKey.class), any(byte[].class));
        NacosException result = assertThrows(NacosException.class,
            () -> service.save(descriptor(false, false), contents(false, false)));
        assertEquals("MCP Version content cannot be saved", result.getErrMsg());
        assertNotNull(result.getCause());
    }
    
    @Test
    void testLoadReadsEveryReferencedObject() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor descriptor = descriptor(true, true);
        when(storage.get(any(StorageKey.class))).thenAnswer(invocation -> contentFor(
            descriptor, invocation.getArgument(0)));
        
        McpVersionStorageContents result = service.load(descriptor);
        
        assertArrayEquals(SERVER, result.getServerContent());
        assertArrayEquals(TOOLS, result.getToolContent());
        assertArrayEquals(RESOURCES, result.getResourceContent());
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        verify(storage, times(3)).get(keyCaptor.capture());
        assertEquals(List.of(descriptor.getServerKey(), descriptor.getToolKey(),
            descriptor.getResourceKey()), keys(keyCaptor));
    }
    
    @Test
    void testLoadSupportsDescriptorWithoutOptionalFiles() throws Exception {
        givenStorage();
        when(storage.get(any(StorageKey.class))).thenReturn(SERVER);
        McpVersionStorageContents result = service.load(descriptor(false, false));
        assertNull(result.getToolContent());
        assertNull(result.getResourceContent());
        verify(storage).get(any(StorageKey.class));
    }
    
    @Test
    void testLoadIfPresentReturnsNullOnlyWhenServerIsMissing() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor descriptor = descriptor(true, true);
        when(storage.get(any(StorageKey.class))).thenReturn(null);
        
        assertNull(service.loadIfPresent(descriptor));
        
        verify(storage).get(any(StorageKey.class));
    }
    
    @Test
    void testLoadIfPresentLoadsCompleteExistingVersion() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor descriptor = descriptor(true, true);
        when(storage.get(any(StorageKey.class))).thenAnswer(invocation -> contentFor(
            descriptor, invocation.getArgument(0)));
        
        McpVersionStorageContents result = service.loadIfPresent(descriptor);
        
        assertArrayEquals(SERVER, result.getServerContent());
        assertArrayEquals(TOOLS, result.getToolContent());
        assertArrayEquals(RESOURCES, result.getResourceContent());
        verify(storage, times(3)).get(any(StorageKey.class));
    }
    
    @Test
    void testLoadIfPresentDoesNotHideOptionalOrProviderFailure() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor descriptor = descriptor(true, false);
        when(storage.get(any(StorageKey.class))).thenReturn(SERVER, null);
        assertEquals("MCP Tools content does not exist",
            assertThrows(NacosException.class,
                () -> service.loadIfPresent(descriptor)).getErrMsg());
        
        NacosException expected = new NacosException(NacosException.SERVER_ERROR, "read failed");
        when(storage.get(any(StorageKey.class))).thenThrow(expected);
        assertEquals(expected,
            assertThrows(NacosException.class,
                () -> service.loadIfPresent(descriptor(false, false))));
    }
    
    @Test
    void testLoadRejectsMissingOrEmptyReferencedContent() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor descriptor = descriptor(true, true);
        when(storage.get(any(StorageKey.class))).thenReturn(null);
        assertEquals("MCP Server content does not exist",
            assertThrows(NacosException.class, () -> service.load(descriptor)).getErrMsg());
        when(storage.get(any(StorageKey.class))).thenReturn(SERVER, null);
        assertEquals("MCP Tools content does not exist",
            assertThrows(NacosException.class, () -> service.load(descriptor)).getErrMsg());
        when(storage.get(any(StorageKey.class))).thenReturn(SERVER, TOOLS, new byte[0]);
        assertEquals("MCP Resources content does not exist",
            assertThrows(NacosException.class, () -> service.load(descriptor)).getErrMsg());
    }
    
    @Test
    void testLoadWrapsInvalidDescriptorAndProviderFailures() {
        McpVersionStorageDescriptor invalid = descriptor(false, false);
        invalid.setServerKey("invalid");
        NacosException descriptorFailure = assertThrows(NacosException.class,
            () -> service.load(invalid));
        assertEquals("Invalid MCP Version storage descriptor", descriptorFailure.getErrMsg());
        when(storageRouter.route(any(StorageKey.class)))
            .thenThrow(new IllegalStateException("missing"));
        NacosException providerFailure = assertThrows(NacosException.class,
            () -> service.load(descriptor(false, false)));
        assertTrue(providerFailure.getErrMsg().contains("provider is unavailable"));
    }
    
    @Test
    void testLoadWrapsProviderKeyRejectionAndPropagatesStorageFailure() throws Exception {
        givenStorage();
        when(storage.get(any(StorageKey.class))).thenThrow(new IllegalArgumentException("bad key"));
        NacosException invalidKey = assertThrows(NacosException.class,
            () -> service.load(descriptor(false, false)));
        assertEquals("Invalid MCP Version storage key", invalidKey.getErrMsg());
        NacosException expected = new NacosException(NacosException.SERVER_ERROR, "read failed");
        when(storage.get(any(StorageKey.class))).thenThrow(expected);
        assertEquals(expected,
            assertThrows(NacosException.class, () -> service.load(descriptor(false, false))));
    }
    
    @Test
    void testDeleteAttemptsEveryObjectInReverseDependencyOrder() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor descriptor = descriptor(true, true);
        NacosException resourceFailure =
            new NacosException(NacosException.SERVER_ERROR, "resource failed");
        doAnswer(invocation -> {
            StorageKey key = invocation.getArgument(0);
            if (descriptor.getResourceKey().equals(key.getKey())) {
                throw resourceFailure;
            }
            if (descriptor.getToolKey().equals(key.getKey())) {
                throw new IllegalArgumentException("tool key rejected");
            }
            return null;
        }).when(storage).delete(any(StorageKey.class));
        
        NacosException result = assertThrows(NacosException.class,
            () -> service.delete(descriptor));
        
        assertEquals(resourceFailure, result);
        assertEquals(1, result.getSuppressed().length);
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        verify(storage, times(3)).delete(keyCaptor.capture());
        assertEquals(List.of(descriptor.getResourceKey(), descriptor.getToolKey(),
            descriptor.getServerKey()), keys(keyCaptor));
    }
    
    @Test
    void testDeleteSupportsMissingOptionalFiles() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor descriptor = descriptor(false, false);
        service.delete(descriptor);
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        verify(storage).delete(keyCaptor.capture());
        assertEquals(descriptor.getServerKey(), keyCaptor.getValue().getKey());
    }
    
    @Test
    void testDeleteObsoleteRemovesOnlyDroppedObjectsInDependencyOrder() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor previous = descriptor(true, true);
        McpVersionStorageDescriptor replacement = descriptor(false, false);
        
        service.deleteObsolete(previous, replacement);
        
        ArgumentCaptor<StorageKey> keyCaptor = ArgumentCaptor.forClass(StorageKey.class);
        verify(storage, times(2)).delete(keyCaptor.capture());
        assertEquals(List.of(previous.getResourceKey(), previous.getToolKey()), keys(keyCaptor));
    }
    
    @Test
    void testDeleteObsoleteRetainsUnchangedDescriptorWithoutRouting() throws Exception {
        McpVersionStorageDescriptor descriptor = descriptor(true, true);
        
        service.deleteObsolete(descriptor, descriptor(true, true));
        
        verifyNoInteractions(storageRouter, storage);
    }
    
    @Test
    void testDeleteObsoleteAttemptsAllDroppedObjectsAndReportsFailures() throws Exception {
        givenStorage();
        McpVersionStorageDescriptor previous = descriptor(true, true);
        McpVersionStorageDescriptor replacement = descriptor(false, false);
        NacosException failure = new NacosException(NacosException.SERVER_ERROR, "failed");
        NacosException secondFailure = new NacosException(NacosException.SERVER_ERROR,
            "failed again");
        doThrow(failure, secondFailure).when(storage).delete(any(StorageKey.class));
        
        NacosException result = assertThrows(NacosException.class,
            () -> service.deleteObsolete(previous, replacement));
        
        assertEquals(failure, result);
        assertEquals(1, result.getSuppressed().length);
        verify(storage, times(2)).delete(any(StorageKey.class));
    }
    
    @Test
    void testDeleteRejectsInvalidDescriptorBeforeRouting() {
        McpVersionStorageDescriptor descriptor = descriptor(false, false);
        descriptor.setSchemaVersion(2);
        assertThrows(NacosException.class, () -> service.delete(descriptor));
        verify(storageRouter, never()).route(any(StorageKey.class));
    }
    
    @Test
    void testContentsAreValidatedAndDefensivelyCopied() {
        assertThrows(IllegalArgumentException.class,
            () -> new McpVersionStorageContents(null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new McpVersionStorageContents(new byte[0], null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new McpVersionStorageContents(SERVER, new byte[0], null));
        assertThrows(IllegalArgumentException.class,
            () -> new McpVersionStorageContents(SERVER, null, new byte[0]));
        byte[] source = SERVER.clone();
        McpVersionStorageContents contents = new McpVersionStorageContents(source, null, null);
        source[0] = 'x';
        assertArrayEquals(SERVER, contents.getServerContent());
        byte[] returned = contents.getServerContent();
        returned[0] = 'y';
        assertArrayEquals(SERVER, contents.getServerContent());
    }
    
    private void givenStorage() {
        when(storageRouter.route(any(StorageKey.class))).thenReturn(storage);
    }
    
    private McpVersionStorageDescriptor descriptor(boolean tools, boolean resources) {
        return McpVersionStorageKeyComposer.compose("public", MCP_ID, "1.0.0", tools, resources);
    }
    
    private McpVersionStorageContents contents(boolean tools, boolean resources) {
        return new McpVersionStorageContents(SERVER, tools ? TOOLS : null,
            resources ? RESOURCES : null);
    }
    
    private byte[] contentFor(McpVersionStorageDescriptor descriptor, StorageKey key) {
        if (descriptor.getServerKey().equals(key.getKey())) {
            return SERVER;
        }
        if (descriptor.getToolKey().equals(key.getKey())) {
            return TOOLS;
        }
        return RESOURCES;
    }
    
    private List<String> keys(ArgumentCaptor<StorageKey> captor) {
        return captor.getAllValues().stream().map(StorageKey::getKey).toList();
    }
}
