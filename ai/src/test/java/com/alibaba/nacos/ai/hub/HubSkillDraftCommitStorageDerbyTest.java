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

import com.alibaba.nacos.ai.service.hub.EmbeddedHubSkillDraftCommitStorage;
import com.alibaba.nacos.ai.service.hub.ExternalHubSkillDraftCommitStorage;
import com.alibaba.nacos.ai.service.hub.HubSkillDraftCommitStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.BaseDatabaseOperate;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class HubSkillDraftCommitStorageDerbyTest {
    
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
    @ValueSource(booleans = {false, true})
    void onlyMatchingIdAndMetaVersionCanPublishOrDelete(boolean embedded) throws Exception {
        long target = insert("target-ns", "copied");
        long competitor = insert("target-ns", "other");
        HubSkillDraftCommitStorage store = adapter(embedded);
        assertFalse(store.publish(competitor, "target-ns", "copied", 1L));
        assertFalse(store.publish(target, "another-ns", "copied", 1L));
        assertTrue(store.publish(target, "target-ns", "copied", 1L));
        assertFalse(store.publish(target, "target-ns", "copied", 1L));
        assertEquals("enable",
            database.queryString("SELECT status FROM ai_resource WHERE id=?", target));
        assertEquals(2L,
            database.queryLong("SELECT meta_version FROM ai_resource WHERE id=?", target));
        assertFalse(store.deleteDisabled(target, "target-ns", "copied", 2L));
        assertTrue(store.disable(target, "target-ns", "copied", 2L));
        assertTrue(store.deleteDisabled(target, "target-ns", "copied", 3L));
        assertEquals(0L, database.queryLong("SELECT count(*) FROM ai_resource WHERE id=?", target));
        assertEquals(1L,
            database.queryLong("SELECT count(*) FROM ai_resource WHERE id=?", competitor));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void draftDeletionRequiresExactVersionAndDisabledReservation(boolean embedded)
        throws Exception {
        long metaId = insert("target-ns", "copied");
        database.update("INSERT INTO ai_resource_version (namespace_id,name,type,version,status) "
            + "VALUES ('target-ns','copied','skill','0.0.1','draft')");
        long versionId =
            database.queryLong("SELECT id FROM ai_resource_version WHERE name='copied'");
        HubSkillDraftCommitStorage store = adapter(embedded);
        assertFalse(
            store.deleteDraftVersion(metaId, "target-ns", "copied", 1L, versionId + 1, "0.0.1"));
        assertFalse(
            store.deleteDraftVersion(metaId, "target-ns", "copied", 2L, versionId, "0.0.1"));
        assertTrue(store.publish(metaId, "target-ns", "copied", 1L));
        assertFalse(
            store.deleteDraftVersion(metaId, "target-ns", "copied", 2L, versionId, "0.0.1"));
        assertTrue(store.disable(metaId, "target-ns", "copied", 2L));
        database.update("UPDATE ai_resource_version SET status='online' WHERE id=?", versionId);
        assertFalse(
            store.deleteDraftVersion(metaId, "target-ns", "copied", 3L, versionId, "0.0.1"));
        database.update("UPDATE ai_resource_version SET status='draft' WHERE id=?", versionId);
        assertTrue(store.deleteDraftVersion(metaId, "target-ns", "copied", 3L, versionId, "0.0.1"));
        assertEquals(0L,
            database.queryLong("SELECT count(*) FROM ai_resource_version WHERE id=?", versionId));
    }
    
    private long insert(String namespaceId, String name) throws Exception {
        database.update("INSERT INTO ai_resource "
            + "(namespace_id,name,type,c_from,status,scope,owner,meta_version,ext,version_info) "
            + "VALUES (?,?,'skill','local','disable','PRIVATE','alice',1,'{}','{}')",
            namespaceId, name);
        return database.queryLong("SELECT id FROM ai_resource WHERE namespace_id=? AND name=?",
            namespaceId, name);
    }
    
    private HubSkillDraftCommitStorage adapter(boolean embedded) {
        if (embedded) {
            DatabaseOperate operate = mock(DatabaseOperate.class, CALLS_REAL_METHODS);
            BaseDatabaseOperate base = mock(BaseDatabaseOperate.class, CALLS_REAL_METHODS);
            doAnswer(i -> base.update(transaction, jdbc,
                i.<List<ModifyRequest>>getArgument(0),
                i.<BiConsumer<Boolean, Throwable>>getArgument(1)))
                .when(operate).update(anyList(), any());
            return new EmbeddedHubSkillDraftCommitStorage(operate);
        }
        DynamicDataSource datasource = mock(DynamicDataSource.class);
        DataSourceService service = mock(DataSourceService.class);
        when(datasource.getDataSource()).thenReturn(service);
        when(service.getJdbcTemplate()).thenReturn(jdbc);
        try (MockedStatic<DynamicDataSource> mockStatic = mockStatic(DynamicDataSource.class)) {
            mockStatic.when(DynamicDataSource::getInstance).thenReturn(datasource);
            return new ExternalHubSkillDraftCommitStorage();
        }
    }
}
