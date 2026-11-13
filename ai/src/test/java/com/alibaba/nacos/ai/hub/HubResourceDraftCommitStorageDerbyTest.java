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

package com.alibaba.nacos.ai.hub;

import com.alibaba.nacos.ai.service.hub.EmbeddedHubResourceDraftCommitStorage;
import com.alibaba.nacos.ai.service.hub.ExternalHubResourceDraftCommitStorage;
import com.alibaba.nacos.ai.service.hub.HubResourceDraftCommitStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.BaseDatabaseOperate;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import java.util.stream.Stream;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Actual Derby SQL checks for both external and embedded commit adapters. */
class HubResourceDraftCommitStorageDerbyTest {
    
    private HubDatabaseTestSupport database;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    
    @BeforeEach
    void setUp() throws Exception {
        database = HubDatabaseTestSupport.open();
        SingleConnectionDataSource ds = new SingleConnectionDataSource(database.connection(), true);
        jdbc = new JdbcTemplate(ds);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    
    @AfterEach
    void tearDown() throws Exception {
        EmbeddedStorageContextHolder.cleanAllContext();
        if (database != null) {
            database.close();
        }
    }
    
    @ParameterizedTest
    @MethodSource("typesAndBackends")
    void onlyMatchingIdAndMetaVersionCanPublishOrDelete(HubResourceType type, boolean embedded)
        throws Exception {
        long target = insert(type, "target-ns", "copied");
        long competitor = insert(type, "target-ns", "other");
        HubResourceDraftCommitStorage store = adapter(embedded);
        assertFalse(store.publish(HubResourceType.SKILL, target, "target-ns", "copied", 1L));
        assertFalse(store.publish(type, competitor, "target-ns", "copied", 1L));
        assertFalse(store.publish(type, target, "another-ns", "copied", 1L));
        assertTrue(store.publish(type, target, "target-ns", "copied", 1L));
        assertFalse(store.publish(type, target, "target-ns", "copied", 1L));
        assertEquals("enable",
            database.queryString("SELECT status FROM ai_resource WHERE id=?", target));
        assertEquals(2L,
            database.queryLong("SELECT meta_version FROM ai_resource WHERE id=?", target));
        assertFalse(store.deleteDisabled(type, target, "target-ns", "copied", 2L));
        assertTrue(store.disable(type, target, "target-ns", "copied", 2L));
        assertTrue(store.deleteDisabled(type, target, "target-ns", "copied", 3L));
        assertEquals(0L, database.queryLong("SELECT count(*) FROM ai_resource WHERE id=?", target));
        assertEquals(1L,
            database.queryLong("SELECT count(*) FROM ai_resource WHERE id=?", competitor));
    }
    
    @ParameterizedTest
    @MethodSource("typesAndBackends")
    void draftDeletionRequiresExactVersionAndDisabledReservation(HubResourceType type,
        boolean embedded)
        throws Exception {
        long metaId = insert(type, "target-ns", "copied");
        database.update("INSERT INTO ai_resource_version (namespace_id,name,type,version,status) "
            + "VALUES ('target-ns','copied',?,'0.0.1','draft')", type.resourceType());
        long versionId =
            database.queryLong("SELECT id FROM ai_resource_version WHERE name='copied'");
        HubResourceDraftCommitStorage store = adapter(embedded);
        assertFalse(
            store.deleteDraftVersion(type, metaId, "target-ns", "copied", 1L, versionId + 1,
                "0.0.1"));
        assertFalse(
            store.deleteDraftVersion(type, metaId, "target-ns", "copied", 2L, versionId, "0.0.1"));
        assertTrue(store.publish(type, metaId, "target-ns", "copied", 1L));
        assertFalse(
            store.deleteDraftVersion(type, metaId, "target-ns", "copied", 2L, versionId, "0.0.1"));
        assertTrue(store.disable(type, metaId, "target-ns", "copied", 2L));
        database.update("UPDATE ai_resource_version SET status='online' WHERE id=?", versionId);
        assertFalse(
            store.deleteDraftVersion(type, metaId, "target-ns", "copied", 3L, versionId, "0.0.1"));
        database.update("UPDATE ai_resource_version SET status='draft' WHERE id=?", versionId);
        assertTrue(
            store.deleteDraftVersion(type, metaId, "target-ns", "copied", 3L, versionId, "0.0.1"));
        assertEquals(0L,
            database.queryLong("SELECT count(*) FROM ai_resource_version WHERE id=?", versionId));
    }
    
    private static Stream<Arguments> typesAndBackends() {
        return Stream.of(HubResourceType.MCP, HubResourceType.AGENT, HubResourceType.PROMPT,
            HubResourceType.AGENTSPEC).flatMap(
                type -> Stream.of(Arguments.of(type, false),
                    Arguments.of(type, true)));
    }
    
    private long insert(HubResourceType type, String namespaceId, String name) throws Exception {
        database.update("INSERT INTO ai_resource "
            + "(namespace_id,name,type,c_from,status,scope,owner,meta_version,ext,version_info) "
            + "VALUES (?,?,?,'local','disable','PRIVATE','alice',1,'{}','{}')",
            namespaceId, name, type.resourceType());
        return database.queryLong("SELECT id FROM ai_resource WHERE namespace_id=? AND name=?",
            namespaceId, name);
    }
    
    private HubResourceDraftCommitStorage adapter(boolean embedded) {
        if (embedded) {
            DatabaseOperate operate = mock(DatabaseOperate.class, CALLS_REAL_METHODS);
            BaseDatabaseOperate base = mock(BaseDatabaseOperate.class, CALLS_REAL_METHODS);
            doAnswer(i -> base.update(transaction, jdbc,
                i.<List<ModifyRequest>>getArgument(0),
                i.<BiConsumer<Boolean, Throwable>>getArgument(1)))
                .when(operate).update(anyList(), any());
            return new EmbeddedHubResourceDraftCommitStorage(operate);
        }
        DynamicDataSource datasource = mock(DynamicDataSource.class);
        DataSourceService service = mock(DataSourceService.class);
        when(datasource.getDataSource()).thenReturn(service);
        when(service.getJdbcTemplate()).thenReturn(jdbc);
        try (MockedStatic<DynamicDataSource> mockStatic = mockStatic(DynamicDataSource.class)) {
            mockStatic.when(DynamicDataSource::getInstance).thenReturn(datasource);
            return new ExternalHubResourceDraftCommitStorage();
        }
    }
}
