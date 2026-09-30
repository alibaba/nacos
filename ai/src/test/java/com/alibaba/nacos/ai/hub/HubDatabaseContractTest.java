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
 *
 */

package com.alibaba.nacos.ai.hub;

import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the checkout's real database constraints and Hub internal-name boundary.
 *
 * <p>These are JDBC/DDL experiments, not a Hub API or Nacos Raft transaction test.
 * Test rows deliberately contain only the fields needed for these experiments.</p>
 *
 * <p>Tests use an isolated local Derby in-memory database and require no Docker.</p>
 *
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class HubDatabaseContractTest {
    
    private static final String HUB = AiConstants.Hub.NAMESPACE_ID;
    
    private static final String VERSION = "1.0.0";
    
    private static final String META_INSERT = "INSERT INTO ai_resource "
        + "(namespace_id, name, type, c_from, status, scope, owner) "
        + "VALUES (?, ?, 'skill', ?, 'disable', 'PUBLIC', 's0-test')";
    
    private HubDatabaseTestSupport database;
    
    @BeforeAll
    void setUpDatabase() throws Exception {
        database = HubDatabaseTestSupport.open();
    }
    
    @BeforeEach
    void resetRows() throws Exception {
        database.reset();
    }
    
    @AfterAll
    void closeDatabase() throws Exception {
        if (database != null) {
            database.close();
        }
    }
    
    @Test
    void schemaShouldExposeExpectedUniqueKeysAndColumnSizes() throws Exception {
        assertTrue(database.uniqueKeys("tenant_info").contains(List.of("kp", "tenant_id")));
        assertFalse(database.uniqueKeys("tenant_info").contains(List.of("tenant_id")));
        assertTrue(database.uniqueKeys("ai_resource")
            .contains(List.of("namespace_id", "name", "type", "c_from")));
        assertTrue(database.uniqueKeys("ai_resource_version")
            .contains(List.of("namespace_id", "name", "type", "version")));
        assertEquals(256, database.columnSize("ai_resource", "name"));
        assertEquals(256, database.columnSize("ai_resource_version", "name"));
    }
    
    @Test
    void sameSourceNameShouldCoexistWithIndependentHubIds() throws Exception {
        long sourceA = insertMeta("source-a", "find-skills", "local");
        long sourceB = insertMeta("source-b", "find-skills", "local");
        insertVersion("source-a", "find-skills", VERSION);
        insertVersion("source-b", "find-skills", VERSION);
        String nameA = HubResourceNameUtils.buildInternalName("find-skills", sourceA);
        String nameB = HubResourceNameUtils.buildInternalName("find-skills", sourceB);
        long hubA = insertMeta(HUB, nameA, "hub");
        long hubB = insertMeta(HUB, nameB, "hub");
        insertVersion(HUB, nameA, VERSION);
        insertVersion(HUB, nameB, VERSION);
        
        assertEquals("find-skills-" + sourceA, nameA);
        assertEquals("find-skills-" + sourceB, nameB);
        assertNotEquals(nameA, nameB);
        assertNotEquals(hubA, hubB);
        assertNotEquals(sourceA, hubA);
        assertNotEquals(sourceB, hubA);
        assertNotEquals(sourceA, hubB);
        assertNotEquals(sourceB, hubB);
        assertEquals(2,
            database.queryLong("SELECT COUNT(*) FROM ai_resource WHERE namespace_id=?", HUB));
        assertEquals(2, database.queryLong(
            "SELECT COUNT(*) FROM ai_resource_version WHERE namespace_id=?", HUB));
    }
    
    @Test
    void newPrimaryKeyDoesNotAllowDuplicateBusinessKey() throws Exception {
        insertMeta(HUB, "find-skills-101", "hub");
        SQLException failure = assertThrows(SQLException.class,
            () -> insertMeta(HUB, "find-skills-101", "hub"));
        assertTrue(database.isDuplicate(failure), failure::toString);
        assertEquals(1, database.queryLong("SELECT COUNT(*) FROM ai_resource"));
    }
    
    @Test
    void changingFromDoesNotSolveVersionCollision() throws Exception {
        insertMeta(HUB, "find-skills", "from-a");
        insertMeta(HUB, "find-skills", "from-b");
        assertEquals(2, database.queryLong(
            "SELECT COUNT(*) FROM ai_resource WHERE namespace_id=? AND name=? AND type='skill'",
            HUB, "find-skills"));
        insertVersion(HUB, "find-skills", VERSION);
        SQLException failure = assertThrows(SQLException.class,
            () -> insertVersion(HUB, "find-skills", VERSION));
        assertTrue(database.isDuplicate(failure), failure::toString);
    }
    
    @Test
    void versionKeyAllowsDifferentVersionsButRejectsSameVersion() throws Exception {
        insertMeta(HUB, "find-skills-101", "hub");
        insertVersion(HUB, "find-skills-101", VERSION);
        insertVersion(HUB, "find-skills-101", "2.0.0");
        SQLException failure = assertThrows(SQLException.class,
            () -> insertVersion(HUB, "find-skills-101", VERSION));
        assertTrue(database.isDuplicate(failure), failure::toString);
        assertEquals(2, database.queryLong("SELECT COUNT(*) FROM ai_resource_version"));
    }
    
    @Test
    void tenantUniqueKeyDoesNotPreventSameIdAcrossDifferentTypes() throws Exception {
        insertTenant("1");
        insertTenant("3");
        assertEquals(2,
            database.queryLong("SELECT COUNT(*) FROM tenant_info WHERE tenant_id=?", HUB));
        assertEquals(1, database.queryLong("SELECT COUNT(*) FROM tenant_info WHERE kp='1'"));
        SQLException failure = assertThrows(SQLException.class, () -> insertTenant("3"));
        assertTrue(database.isDuplicate(failure), failure::toString);
    }
    
    @Test
    void rawAsciiBoundaryShouldBeEnforcedByBothTables() throws Exception {
        roundTrip("a".repeat(256));
        String tooLong = "a".repeat(257);
        SQLException metaFailure =
            assertThrows(SQLException.class, () -> insertMeta(HUB, tooLong, "hub"));
        assertTrue(database.isTooLong(metaFailure), metaFailure::toString);
        SQLException versionFailure =
            assertThrows(SQLException.class, () -> insertVersion(HUB, tooLong, VERSION));
        assertTrue(database.isTooLong(versionFailure), versionFailure::toString);
    }
    
    @Test
    void longAsciiNameShouldRetainMaxIdSuffix() throws Exception {
        String result = HubResourceNameUtils.buildInternalName("a".repeat(256), Long.MAX_VALUE);
        assertEquals("a".repeat(236) + "-9223372036854775807", result);
        roundTrip(result);
    }
    
    @Test
    void truncatedNamesWithDifferentIdsShouldStillCoexist() throws Exception {
        String source = "a".repeat(256);
        String first = HubResourceNameUtils.buildInternalName(source, 101L);
        String second = HubResourceNameUtils.buildInternalName(source, 201L);
        assertNotEquals(first, second);
        assertTrue(first.endsWith("-101"));
        assertTrue(second.endsWith("-201"));
        roundTrip(first);
        roundTrip(second);
    }
    
    //    @Test
    //    void bmpGeneratedNameShouldRoundTripWithoutLosingSuffix() throws Exception {
    //        // Source: 235 UTF-16 units / 237 UTF-8 bytes. With max-ID suffix: 257 UTF-8 bytes.
    //        String source = "a".repeat(234) + "\u6280";
    //        insertMeta("unicode-source", source, "local");
    //        String result = HubResourceNameUtils.buildInternalName(source, Long.MAX_VALUE);
    //        reportLengths("bmp", result);
    //        assertTrue(result.endsWith("-9223372036854775807"));
    //        assertWellFormedUtf16(result);
    //        roundTrip(result);
    //    }
    //
    //    @Test
    //    void supplementaryGeneratedNameShouldRoundTripWithoutLosingSuffix() throws Exception {
    //        // Source fits VARCHAR(256), but the previous code-point rule yields 257 UTF-16 units.
    //        String source = "a".repeat(235) + "\uD83D\uDE00";
    //        insertMeta("unicode-source", source, "local");
    //        String result = HubResourceNameUtils.buildInternalName(source, Long.MAX_VALUE);
    //        reportLengths("supplementary", result);
    //        assertTrue(result.endsWith("-9223372036854775807"));
    //        assertWellFormedUtf16(result);
    //        roundTrip(result);
    //    }
    
    @Test
    void concurrentDuplicateInsertShouldHaveOneWinner() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> concurrentInsert(ready, start));
            Future<Boolean> second = executor.submit(() -> concurrentInsert(ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            boolean firstWon = first.get(30, TimeUnit.SECONDS);
            boolean secondWon = second.get(30, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon);
            assertEquals(1, database.queryLong("SELECT COUNT(*) FROM ai_resource"));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(35, TimeUnit.SECONDS));
        }
    }
    
    @Test
    void autoCommitCanLeaveMetaAfterVersionInsertFailure() throws Exception {
        insertVersion(HUB, "find-skills-101", VERSION);
        insertMeta(HUB, "find-skills-101", "hub");
        SQLException failure = assertThrows(SQLException.class,
            () -> insertVersion(HUB, "find-skills-101", VERSION));
        assertTrue(database.isDuplicate(failure), failure::toString);
        assertEquals(1, database.queryLong("SELECT COUNT(*) FROM ai_resource"));
    }
    
    @Test
    void explicitJdbcTransactionCanRollbackMetaAfterVersionInsertFailure() throws Exception {
        insertVersion(HUB, "find-skills-101", VERSION);
        Connection connection = database.connection();
        connection.setAutoCommit(false);
        try {
            insertMeta(HUB, "find-skills-101", "hub");
            SQLException failure = assertThrows(SQLException.class,
                () -> insertVersion(HUB, "find-skills-101", VERSION));
            assertTrue(database.isDuplicate(failure), failure::toString);
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
        assertEquals(0, database.queryLong("SELECT COUNT(*) FROM ai_resource"));
        assertEquals(1, database.queryLong("SELECT COUNT(*) FROM ai_resource_version"));
    }
    
    private boolean concurrentInsert(CountDownLatch ready, CountDownLatch start) throws Exception {
        try (Connection connection = database.newConnection()) {
            ready.countDown();
            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent experiment did not start");
            }
            try {
                HubDatabaseTestSupport.update(connection, META_INSERT, HUB, "find-skills-101",
                    "hub");
                return true;
            } catch (SQLException failure) {
                if (database.isDuplicate(failure)) {
                    return false;
                }
                throw failure;
            }
        }
    }
    
    private long insertMeta(String namespaceId, String name, String from) throws SQLException {
        database.update(META_INSERT, namespaceId, name, from);
        return database.queryLong("SELECT id FROM ai_resource "
            + "WHERE namespace_id=? AND name=? AND type='skill' AND c_from=?", namespaceId, name,
            from);
    }
    
    private void insertVersion(String namespaceId, String name, String version)
        throws SQLException {
        database.update("INSERT INTO ai_resource_version "
            + "(namespace_id, name, type, version, status) VALUES (?, ?, 'skill', ?, 'online')",
            namespaceId, name, version);
    }
    
    private void insertTenant(String kp) throws SQLException {
        Object now = database.tenantTime();
        database.update("INSERT INTO tenant_info "
            + "(kp, tenant_id, tenant_name, gmt_create, gmt_modified) VALUES (?, ?, ?, ?, ?)",
            kp, HUB, "Hub S0 experiment", now, now);
    }
    
    private void roundTrip(String name) throws SQLException {
        long id = insertMeta(HUB, name, "hub");
        insertVersion(HUB, name, VERSION);
        assertEquals(name, database.queryString("SELECT name FROM ai_resource WHERE id=?", id));
        assertEquals(name, database.queryString("SELECT name FROM ai_resource_version "
            + "WHERE namespace_id=? AND name=? AND type='skill' AND version=?", HUB, name,
            VERSION));
    }
    
    private void reportLengths(String label, String value) {
        System.out.println("[Hub S0-4] " + label + ": utf16=" + value.length()
            + ", codePoints=" + value.codePointCount(0, value.length())
            + ", utf8Bytes=" + value.getBytes(StandardCharsets.UTF_8).length);
    }
    
    private void assertWellFormedUtf16(String text) {
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isHighSurrogate(current)) {
                assertTrue(i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1)));
                i++;
            } else {
                assertTrue(!Character.isLowSurrogate(current));
            }
        }
    }
}
