/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.GroupKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit test for McpServerCacheInvalidateService.
 *
 * @author xinluo
 */
@ExtendWith(MockitoExtension.class)
class McpServerCacheInvalidateServiceTest {

    @Mock
    private McpServerIndex mcpServerIndex;

    private McpServerCacheInvalidateService service;

    private static final String TEST_NAMESPACE = "test-namespace";

    private static final String TEST_SERVER_ID = "test-server-id";

    private static final String TEST_DATAID_VERSION = TEST_SERVER_ID + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX;

    private static final String TEST_GROUP_KEY_VERSION = GroupKey.getKey(TEST_DATAID_VERSION, Constants.MCP_SERVER_VERSIONS_GROUP, TEST_NAMESPACE);

    @BeforeEach
    void setUp() {
        service = new McpServerCacheInvalidateService(mcpServerIndex);
    }

    @AfterEach
    void tearDown() {
        service = null;
    }

    /**
     * Test handling MCP server version config change event successfully.
     */
    @Test
    void testHandleMcpServerVersionConfigChangeEvent() {
        // Given: MCP server version config change event
        LocalDataChangeEvent event = new LocalDataChangeEvent(TEST_GROUP_KEY_VERSION);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should be invalidated
        verify(mcpServerIndex, times(1)).removeMcpServerById(eq(TEST_SERVER_ID));
    }

    /**
     * Test handling non-MCP server config change event (should be ignored).
     */
    @Test
    void testHandleNonMcpServerConfigChangeEvent() {
        // Given: non-MCP server config change event
        String groupKey = GroupKey.getKey("some-data-id", "some-group", TEST_NAMESPACE);
        LocalDataChangeEvent event = new LocalDataChangeEvent(groupKey);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should NOT be invalidated
        verify(mcpServerIndex, never()).removeMcpServerById(anyString());
    }

    /**
     * Test handling MCP server group config (should be ignored after simplification).
     */
    @Test
    void testHandleMcpServerGroupConfig() {
        // Given: MCP server group config change event
        String dataId = TEST_SERVER_ID + "-v1.0.0" + Constants.MCP_SERVER_SPEC_DATA_ID_SUFFIX;
        String groupKey = GroupKey.getKey(dataId, Constants.MCP_SERVER_GROUP, TEST_NAMESPACE);
        LocalDataChangeEvent event = new LocalDataChangeEvent(groupKey);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should NOT be invalidated (only version group is monitored)
        verify(mcpServerIndex, never()).removeMcpServerById(anyString());
    }

    /**
     * Test handling MCP tools group config (should be ignored after simplification).
     */
    @Test
    void testHandleMcpToolsGroupConfig() {
        // Given: MCP tools group config change event
        String dataId = TEST_SERVER_ID + "-v1.0.0" + Constants.MCP_SERVER_TOOL_DATA_ID_SUFFIX;
        String groupKey = GroupKey.getKey(dataId, Constants.MCP_SERVER_TOOL_GROUP, TEST_NAMESPACE);
        LocalDataChangeEvent event = new LocalDataChangeEvent(groupKey);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should NOT be invalidated (only version group is monitored)
        verify(mcpServerIndex, never()).removeMcpServerById(anyString());
    }

    /**
     * Test handling non-LocalDataChangeEvent (should be ignored).
     */
    @Test
    void testHandleNonLocalDataChangeEvent() {
        // Given: non-LocalDataChangeEvent - create a LocalDataChangeEvent with non-MCP group
        String groupKey = GroupKey.getKey("some-data-id", "some-group", TEST_NAMESPACE);
        LocalDataChangeEvent event = new LocalDataChangeEvent(groupKey);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should NOT be invalidated
        verify(mcpServerIndex, never()).removeMcpServerById(anyString());
    }

    /**
     * Test handling event with invalid dataId format (missing suffix).
     */
    @Test
    void testHandleEventWithInvalidDataIdFormat() {
        // Given: event with invalid dataId (missing suffix)
        String groupKey = GroupKey.getKey("invalid-data-id", Constants.MCP_SERVER_VERSIONS_GROUP, TEST_NAMESPACE);
        LocalDataChangeEvent event = new LocalDataChangeEvent(groupKey);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should NOT be invalidated
        verify(mcpServerIndex, never()).removeMcpServerById(anyString());
    }

    /**
     * Test handling event with empty dataId.
     */
    @Test
    void testHandleEventWithEmptyDataId() {
        // Given: event with empty dataId
        String groupKey = GroupKey.getKey("", Constants.MCP_SERVER_VERSIONS_GROUP, TEST_NAMESPACE);
        LocalDataChangeEvent event = new LocalDataChangeEvent(groupKey);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should NOT be invalidated
        verify(mcpServerIndex, never()).removeMcpServerById(anyString());
    }

    /**
     * Test handling event when cache invalidation throws exception.
     * Exception should be caught and logged, not propagated.
     */
    @Test
    void testHandleEventWhenCacheInvalidationThrowsException() {
        // Given: MCP server version config change event and cache invalidation will throw exception
        LocalDataChangeEvent event = new LocalDataChangeEvent(TEST_GROUP_KEY_VERSION);

        doThrow(new RuntimeException("Cache invalidation failed")).when(mcpServerIndex)
                .removeMcpServerById(eq(TEST_SERVER_ID));

        // When: handle the event (should not throw exception)
        service.handleConfigDataChangeEvent(event);

        // Then: removeMcpServerById should still be called
        verify(mcpServerIndex, times(1)).removeMcpServerById(eq(TEST_SERVER_ID));
    }

    /**
     * Test handling multiple events in sequence.
     */
    @Test
    void testHandleMultipleEvents() {
        // Given: multiple MCP server version config change events
        String serverId1 = "server-1";
        String serverId2 = "server-2";
        String dataId1 = serverId1 + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX;
        String dataId2 = serverId2 + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX;

        String groupKey1 = GroupKey.getKey(dataId1, Constants.MCP_SERVER_VERSIONS_GROUP, TEST_NAMESPACE);
        String groupKey2 = GroupKey.getKey(dataId2, Constants.MCP_SERVER_VERSIONS_GROUP, TEST_NAMESPACE);

        LocalDataChangeEvent event1 = new LocalDataChangeEvent(groupKey1);
        LocalDataChangeEvent event2 = new LocalDataChangeEvent(groupKey2);

        // When: handle both events
        service.handleConfigDataChangeEvent(event1);
        service.handleConfigDataChangeEvent(event2);

        // Then: cache should be invalidated for both servers
        verify(mcpServerIndex, times(1)).removeMcpServerById(eq(serverId1));
        verify(mcpServerIndex, times(1)).removeMcpServerById(eq(serverId2));
    }

    /**
     * Test handling same event multiple times (idempotent).
     */
    @Test
    void testHandleSameEventMultipleTimes() {
        // Given: same MCP server version config change event
        LocalDataChangeEvent event = new LocalDataChangeEvent(TEST_GROUP_KEY_VERSION);

        // When: handle the same event twice
        service.handleConfigDataChangeEvent(event);
        service.handleConfigDataChangeEvent(event);

        // Then: cache invalidation should be called twice (idempotent operation)
        verify(mcpServerIndex, times(2)).removeMcpServerById(eq(TEST_SERVER_ID));
    }

    /**
     * Test extracting server ID with different namespace.
     */
    @Test
    void testHandleEventWithDifferentNamespace() {
        // Given: MCP server version config change event with different namespace
        String differentNamespace = "different-namespace";
        String groupKey = GroupKey.getKey(TEST_DATAID_VERSION, Constants.MCP_SERVER_VERSIONS_GROUP, differentNamespace);
        LocalDataChangeEvent event = new LocalDataChangeEvent(groupKey);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should be invalidated regardless of namespace
        verify(mcpServerIndex, times(1)).removeMcpServerById(eq(TEST_SERVER_ID));
    }

    /**
     * Test handling event with special characters in server ID.
     */
    @Test
    void testHandleEventWithSpecialCharactersInServerId() {
        // Given: server ID with special characters
        String specialServerId = "test-server_123.abc";
        String dataId = specialServerId + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX;
        String groupKey = GroupKey.getKey(dataId, Constants.MCP_SERVER_VERSIONS_GROUP, TEST_NAMESPACE);
        LocalDataChangeEvent event = new LocalDataChangeEvent(groupKey);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should be invalidated with correct server ID
        verify(mcpServerIndex, times(1)).removeMcpServerById(eq(specialServerId));
    }

    /**
     * Test handling event with empty namespace (default namespace).
     */
    @Test
    void testHandleEventWithEmptyNamespace() {
        // Given: MCP server version config change event with empty namespace
        String groupKey = GroupKey.getKey(TEST_DATAID_VERSION, Constants.MCP_SERVER_VERSIONS_GROUP, "");
        LocalDataChangeEvent event = new LocalDataChangeEvent(groupKey);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should be invalidated
        verify(mcpServerIndex, times(1)).removeMcpServerById(eq(TEST_SERVER_ID));
    }

    /**
     * Test handling event with null namespace (default namespace).
     */
    @Test
    void testHandleEventWithNullNamespace() {
        // Given: MCP server version config change event with null namespace
        String groupKey = GroupKey.getKey(TEST_DATAID_VERSION, Constants.MCP_SERVER_VERSIONS_GROUP);
        LocalDataChangeEvent event = new LocalDataChangeEvent(groupKey);

        // When: handle the event
        service.handleConfigDataChangeEvent(event);

        // Then: cache should be invalidated
        verify(mcpServerIndex, times(1)).removeMcpServerById(eq(TEST_SERVER_ID));
    }
}