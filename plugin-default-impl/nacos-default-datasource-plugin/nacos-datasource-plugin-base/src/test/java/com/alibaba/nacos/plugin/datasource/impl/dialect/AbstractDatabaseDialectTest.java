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

import com.alibaba.nacos.plugin.datasource.constants.PrimaryKeyConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractDatabaseDialectTest {
    
    private final AbstractDatabaseDialect dialect = new TestDatabaseDialect();
    
    @Test
    void testPaginationSql() {
        assertEquals(20, dialect.getPagePrevNum(3, 10));
        assertEquals(10, dialect.getPageLastNum(3, 10));
        assertEquals("SELECT LIMIT ? ", dialect.getLimitTopSqlWithMark("SELECT"));
        assertEquals("SELECT LIMIT ?,? ", dialect.getLimitPageSqlWithMark("SELECT"));
        assertEquals("SELECT  LIMIT 20 , 10", dialect.getLimitPageSql("SELECT", 3, 10));
        assertEquals("SELECT  LIMIT 5 , 10", dialect.getLimitPageSqlWithOffset("SELECT", 5, 10));
        assertArrayEquals(PrimaryKeyConstant.LOWER_RETURN_PRIMARY_KEYS,
            dialect.getReturnPrimaryKeys());
        assertEquals("test", dialect.getType());
        assertEquals("NOW()", dialect.getFunction("NOW()"));
    }
    
    private static class TestDatabaseDialect extends AbstractDatabaseDialect {
        
        @Override
        public String getType() {
            return "test";
        }
        
        @Override
        public String getFunction(String functionName) {
            return functionName;
        }
    }
}
