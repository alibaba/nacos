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

package com.alibaba.nacos.plugin.auth.impl.persistence.handler.support;

import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.plugin.auth.impl.model.OffsetFetchResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageHandlerAdapterSupportTest {
    
    @Test
    void testDefaultAdapterReturnsOriginalSqlAndArgs() {
        DefaultPageHandlerAdapter adapter = new DefaultPageHandlerAdapter();
        Object[] args = new Object[] {"nacos"};
        
        OffsetFetchResult result =
            adapter.addOffsetAndFetchNext("select * from users", args, 2, 10);
        
        assertFalse(adapter.supports(PersistenceConstant.MYSQL));
        assertEquals("select * from users", result.getFetchSql());
        assertArrayEquals(args, result.getNewArgs());
    }
    
    @Test
    void testMysqlAdapterAddsLimit() {
        MysqlPageHandlerAdapter adapter = new MysqlPageHandlerAdapter();
        
        OffsetFetchResult result = adapter.addOffsetAndFetchNext("select * from users",
            new Object[] {"nacos"}, 3, 20);
        
        assertTrue(adapter.supports(PersistenceConstant.MYSQL));
        assertFalse(adapter.supports(PersistenceConstant.DERBY));
        assertEquals("select * from users LIMIT ?,?", result.getFetchSql());
        assertArrayEquals(new Object[] {"nacos", 40, 20}, result.getNewArgs());
    }
    
    @Test
    void testMysqlAdapterKeepsSqlWithLimit() {
        MysqlPageHandlerAdapter adapter = new MysqlPageHandlerAdapter();
        Object[] args = new Object[] {"nacos", 10};
        
        OffsetFetchResult result = adapter.addOffsetAndFetchNext(
            "select * from users LIMIT ?", args, 1, 10);
        
        assertEquals("select * from users LIMIT ?", result.getFetchSql());
        assertArrayEquals(args, result.getNewArgs());
    }
    
    @Test
    void testDerbyAdapterAddsOffsetAndFetchNext() {
        DerbyPageHandlerAdapter adapter = new DerbyPageHandlerAdapter();
        
        OffsetFetchResult result = adapter.addOffsetAndFetchNext("select * from users",
            new Object[] {"nacos"}, 2, 15);
        
        assertTrue(adapter.supports(PersistenceConstant.DERBY));
        assertFalse(adapter.supports(PersistenceConstant.MYSQL));
        assertEquals("select * from users OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            result.getFetchSql());
        assertArrayEquals(new Object[] {"nacos", 15, 15}, result.getNewArgs());
    }
    
    @Test
    void testDerbyAdapterKeepsSqlWithOffset() {
        DerbyPageHandlerAdapter adapter = new DerbyPageHandlerAdapter();
        Object[] args = new Object[] {"nacos", 0, 10};
        
        OffsetFetchResult result = adapter.addOffsetAndFetchNext(
            "select * from users OFFSET ? ROWS", args, 1, 10);
        
        assertEquals("select * from users OFFSET ? ROWS", result.getFetchSql());
        assertArrayEquals(args, result.getNewArgs());
    }
}
