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

package com.alibaba.nacos.plugin.auth.impl.persistence.embedded;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthEmbeddedPaginationHelperImplTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    @Mock
    private RowMapper<String> rowMapper;
    
    private AuthEmbeddedPaginationHelperImpl<String> helper;
    
    @BeforeEach
    void setUp() {
        helper = new AuthEmbeddedPaginationHelperImpl<>(databaseOperate);
    }
    
    @Test
    void testFetchPageAddsOffsetAndReturnsItems() {
        when(databaseOperate.queryOne(eq("countSql"), any(Object[].class), eq(Integer.class)))
            .thenReturn(3);
        when(databaseOperate.queryMany(anyString(), any(Object[].class), same(rowMapper)))
            .thenReturn(Collections.singletonList("one"));
        
        Page<String> page =
            helper.fetchPage("countSql", "selectSql", new Object[] {"tenant"}, 1, 2,
                rowMapper);
        
        assertEquals(1, page.getPageNumber());
        assertEquals(2, page.getPagesAvailable());
        assertEquals(3, page.getTotalCount());
        assertEquals(Collections.singletonList("one"), page.getPageItems());
    }
    
    @Test
    void testFetchPageRejectsInvalidInputAndNullCount() {
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPage("countSql", "selectSql", new Object[0], 1, 0, rowMapper));
        when(databaseOperate.queryOne(eq("countSql"), any(Object[].class), eq(Integer.class)))
            .thenReturn(null);
        
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPage("countSql", "selectSql", new Object[0], 1, 10, rowMapper));
    }
    
    @Test
    void testFetchPageLimitWithoutCountArgs() {
        when(databaseOperate.queryOne("countSql", Integer.class)).thenReturn(1);
        when(databaseOperate.queryMany(anyString(), any(Object[].class), same(rowMapper)))
            .thenReturn(Collections.singletonList("one"));
        
        Page<String> page =
            helper.fetchPageLimit("countSql", "selectSql", new Object[0], 1, 10, rowMapper);
        
        assertEquals(1, page.getTotalCount());
        assertEquals(Collections.singletonList("one"), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitWithoutCountArgsRejectsInvalidAndNullCount() {
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPageLimit("countSql", "selectSql", new Object[0], 0, 10,
                rowMapper));
        when(databaseOperate.queryOne("countSql", Integer.class)).thenReturn(null);
        
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPageLimit("countSql", "selectSql", new Object[0], 1, 10,
                rowMapper));
    }
    
    @Test
    void testFetchPageLimitWithoutCountArgsReturnsEmptyWhenPageBeyondCount() {
        when(databaseOperate.queryOne("countSql", Integer.class)).thenReturn(1);
        
        Page<String> page =
            helper.fetchPageLimit("countSql", "selectSql", new Object[0], 2, 10, rowMapper);
        
        assertEquals(2, page.getPageNumber());
        assertEquals(1, page.getPagesAvailable());
        assertEquals(1, page.getTotalCount());
        assertEquals(Collections.emptyList(), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitWithSeparateCountAndFetchArgs() {
        when(databaseOperate.queryOne(eq("countSql"), any(Object[].class), eq(Integer.class)))
            .thenReturn(1);
        when(databaseOperate.queryMany(anyString(), any(Object[].class), same(rowMapper)))
            .thenReturn(Collections.singletonList("one"));
        
        Page<String> page = helper.fetchPageLimit("countSql", new Object[] {"tenant"},
            "selectSql", new Object[] {"user"}, 1, 10, rowMapper);
        
        assertEquals(1, page.getTotalCount());
        assertEquals(Collections.singletonList("one"), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitWithSeparateCountArgsRejectsInvalidAndNullCount() {
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPageLimit("countSql", new Object[0], "selectSql", new Object[0],
                1, 0, rowMapper));
        when(databaseOperate.queryOne(eq("countSql"), any(Object[].class), eq(Integer.class)))
            .thenReturn(null);
        
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPageLimit("countSql", new Object[0], "selectSql", new Object[0],
                1, 10, rowMapper));
    }
    
    @Test
    void testFetchPageLimitWithSeparateCountArgsReturnsEmptyWhenPageBeyondCount() {
        when(databaseOperate.queryOne(eq("countSql"), any(Object[].class), eq(Integer.class)))
            .thenReturn(1);
        
        Page<String> page = helper.fetchPageLimit("countSql", new Object[0], "selectSql",
            new Object[0], 2, 10, rowMapper);
        
        assertEquals(2, page.getPageNumber());
        assertEquals(1, page.getPagesAvailable());
        assertEquals(1, page.getTotalCount());
        assertEquals(Collections.emptyList(), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitWithMapperResult() {
        when(databaseOperate.queryOne(eq("countSql"), any(Object[].class), eq(Integer.class)))
            .thenReturn(1);
        when(databaseOperate.queryMany(anyString(), any(Object[].class), same(rowMapper)))
            .thenReturn(Collections.singletonList("one"));
        MapperResult count = new MapperResult("countSql", Collections.singletonList("tenant"));
        MapperResult fetch = new MapperResult("selectSql", Collections.singletonList("user"));
        
        Page page = helper.fetchPageLimit(count, fetch, 1, 10, rowMapper);
        
        assertEquals(1, page.getTotalCount());
        assertEquals(Collections.singletonList("one"), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitFetchOnly() {
        when(databaseOperate.queryMany(anyString(), any(Object[].class), same(rowMapper)))
            .thenReturn(Collections.singletonList("one"));
        
        Page<String> page =
            helper.fetchPageLimit("selectSql", new Object[] {"user"}, 1, 10, rowMapper);
        
        assertEquals(Collections.singletonList("one"), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitFetchOnlyRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPageLimit("selectSql", new Object[0], 1, 0, rowMapper));
    }
    
    @Test
    void testUpdateLimit() {
        when(databaseOperate.update(anyList())).thenReturn(Boolean.TRUE);
        
        helper.updateLimit("updateSql", new Object[] {"user"});
        
        verify(databaseOperate).update(anyList());
    }
}
