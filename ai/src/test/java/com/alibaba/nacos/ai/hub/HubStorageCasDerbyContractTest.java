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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JDBC contract for the identity-pinned Hub SQL used by ExternalHubEntryStorage
 * and EmbeddedHubEntryStorage. Requires the pre-existing S0 HubDatabaseTestSupport.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class HubStorageCasDerbyContractTest {
    
    private static final String HUB = AiConstants.Hub.NAMESPACE_ID;
    
    private static final String INSERT_META = "INSERT INTO ai_resource "
        + "(namespace_id, name, type, c_from, status, scope, owner, meta_version, ext, version_info) "
        + "VALUES (?, ?, 'skill', ?, 'disable', 'PUBLIC', 'nacos-ai-hub', 1, '{}', '{}')";
    
    private static final String CAS_SQL = "UPDATE ai_resource SET status=?, ext=?, "
        + "version_info=?, meta_version=meta_version+1 "
        + "WHERE id=? AND namespace_id=? AND name=? AND type='skill' "
        + "AND c_from='hub' AND meta_version=?";
    
    private HubDatabaseTestSupport database;
    
    @BeforeAll
    void setUp() throws Exception {
        database = HubDatabaseTestSupport.open();
    }
    
    @BeforeEach
    void clean() throws Exception {
        database.reset();
    }
    
    @AfterAll
    void tearDown() throws Exception {
        if (database != null) {
            database.close();
        }
    }
    
    @Test
    void sameNameNonHubRowMustNotBeSelectedOrUpdated() throws Exception {
        database.update(INSERT_META, HUB, "find-skills-101", "hub");
        database.update(INSERT_META, HUB, "find-skills-101", "local");
        long hubId = database.queryLong("SELECT id FROM ai_resource "
            + "WHERE namespace_id=? AND name=? AND c_from='hub'", HUB, "find-skills-101");
        long localId = database.queryLong("SELECT id FROM ai_resource "
            + "WHERE namespace_id=? AND name=? AND c_from='local'", HUB, "find-skills-101");
        assertNotEquals(hubId, localId);
        assertEquals(1, database.update(CAS_SQL, "enable", "reference-v1", "labels-v1",
            hubId, HUB, "find-skills-101", 1));
        assertEquals(0, database.update(CAS_SQL, "enable", "reference-v2", "labels-v2",
            localId, HUB, "find-skills-101", 1));
        assertEquals("reference-v1", database.queryString(
            "SELECT ext FROM ai_resource WHERE id=?", hubId));
        assertEquals("{}", database.queryString(
            "SELECT ext FROM ai_resource WHERE id=?", localId));
    }
    
    @Test
    void staleOrWrongIdCasMustNotCommit() throws Exception {
        database.update(INSERT_META, HUB, "find-skills-101", "hub");
        long id = database.queryLong("SELECT id FROM ai_resource WHERE name=? "
            + "AND namespace_id=? AND c_from='hub'", "find-skills-101", HUB);
        assertEquals(0, database.update(CAS_SQL, "enable", "v1", "p1",
            id + 999, HUB, "find-skills-101", 1));
        assertEquals(1, database.update(CAS_SQL, "enable", "v1", "p1",
            id, HUB, "find-skills-101", 1));
        assertEquals(0, database.update(CAS_SQL, "enable", "v2", "p2",
            id, HUB, "find-skills-101", 1));
        assertEquals(2, database.queryLong(
            "SELECT meta_version FROM ai_resource WHERE id=?", id));
        assertEquals("v1", database.queryString("SELECT ext FROM ai_resource WHERE id=?", id));
    }
    
    @Test
    void twoConcurrentCasWritesMustHaveExactlyOneWinner() throws Exception {
        database.update(INSERT_META, HUB, "find-skills-101", "hub");
        long id = database.queryLong("SELECT id FROM ai_resource WHERE c_from='hub'");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> raceCas(id, "v1", ready, start));
            Future<Integer> second = executor.submit(() -> raceCas(id, "v2", ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int affected = first.get(25, TimeUnit.SECONDS) + second.get(25, TimeUnit.SECONDS);
            assertEquals(1, affected);
            assertEquals(2, database.queryLong(
                "SELECT meta_version FROM ai_resource WHERE id=?", id));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }
    
    @Test
    void failedReferenceVersionInsertLeavesEntryDisabled() throws Exception {
        database.update(INSERT_META, HUB, "find-skills-101", "hub");
        String insert = "INSERT INTO ai_resource_version "
            + "(namespace_id,name,type,version,status,storage) "
            + "VALUES (?,?,'skill','1.0.0','online',?)";
        database.update(insert, HUB, "find-skills-101", "reference-v1");
        assertThrows(SQLException.class,
            () -> database.update(insert, HUB, "find-skills-101", "reference-v2"));
        assertEquals(1, database.queryLong("SELECT COUNT(*) FROM ai_resource_version"));
        assertEquals("reference-v1", database.queryString(
            "SELECT storage FROM ai_resource_version WHERE name=?", "find-skills-101"));
        assertEquals("disable", database.queryString(
            "SELECT status FROM ai_resource WHERE name=? AND c_from='hub'", "find-skills-101"));
    }
    
    private int raceCas(long id, String version, CountDownLatch ready,
        CountDownLatch start) throws Exception {
        try (Connection connection = database.newConnection()) {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            return HubDatabaseTestSupport.update(connection, CAS_SQL, "enable",
                "ref-" + version, "labels-" + version, id, HUB, "find-skills-101", 1);
        }
    }
}
