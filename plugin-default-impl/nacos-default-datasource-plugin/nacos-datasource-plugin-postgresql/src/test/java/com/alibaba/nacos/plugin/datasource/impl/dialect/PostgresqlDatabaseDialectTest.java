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

package com.alibaba.nacos.plugin.datasource.impl.dialect;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresqlDatabaseDialectTest {
    
    private final PostgresqlDatabaseDialect dialect = new PostgresqlDatabaseDialect();
    
    @Test
    void testIsDuplicateKeyExceptionForUniqueViolationSqlState() {
        assertTrue(dialect.isDuplicateKeyException(
            new SQLException("duplicate key value violates unique constraint", "23505")));
    }
    
    @Test
    void testIsDuplicateKeyExceptionForWrappedUniqueViolationSqlState() {
        // The unique_violation may be wrapped by the driver or the persistence layer, so the
        // classification must walk the cause chain rather than only inspect the top throwable.
        assertTrue(dialect.isDuplicateKeyException(
            new RuntimeException("wrapped", new SQLException("duplicate", "23505"))));
    }
    
    @Test
    void testIsDuplicateKeyExceptionFalseForOtherSqlState() {
        // A different SQLState (for example syntax_error) is not a duplicate-key conflict.
        assertFalse(dialect.isDuplicateKeyException(new SQLException("syntax error", "42601")));
    }
    
    @Test
    void testIsDuplicateKeyExceptionFalseForNonSqlException() {
        assertFalse(dialect.isDuplicateKeyException(new RuntimeException("boom")));
    }
}
