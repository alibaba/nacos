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

package com.alibaba.nacos.plugin.auth.impl.persistence.extrnal;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.plugin.auth.impl.persistence.handler.PageHandlerAdapter;
import com.alibaba.nacos.plugin.auth.impl.persistence.handler.support.DefaultPageHandlerAdapter;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthExternalPaginationHelperImplTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private RowMapper<String> rowMapper;
    
    private AuthExternalPaginationHelperImpl<String> helper;
    
    @BeforeEach
    void setUp() {
        helper = new AuthExternalPaginationHelperImpl<>(jdbcTemplate, PersistenceConstant.MYSQL);
    }
    
    @Test
    void testFetchPageAddsLimitAndReturnsItems() {
        when(jdbcTemplate.queryForObject(eq("countSql"), any(Object[].class),
            eq(Integer.class))).thenReturn(5);
        when(jdbcTemplate.query(anyString(), any(Object[].class), same(rowMapper)))
            .thenReturn(Arrays.asList("one", "two"));
        
        Page<String> page =
            helper.fetchPage("countSql", "selectSql", new Object[] {"tenant"}, 2, 2,
                rowMapper);
        
        assertEquals(2, page.getPageNumber());
        assertEquals(3, page.getPagesAvailable());
        assertEquals(5, page.getTotalCount());
        assertEquals(Arrays.asList("one", "two"), page.getPageItems());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(Object[].class), same(rowMapper));
        assertEquals("selectSql LIMIT ?,?", sqlCaptor.getValue());
    }
    
    @Test
    void testFetchPageReturnsEmptyWhenPageBeyondPageCount() {
        when(jdbcTemplate.queryForObject(eq("countSql"), any(Object[].class),
            eq(Integer.class))).thenReturn(1);
        
        Page<String> page =
            helper.fetchPage("countSql", "selectSql", new Object[0], 2, 10, 100L,
                rowMapper);
        
        assertEquals(2, page.getPageNumber());
        assertEquals(1, page.getPagesAvailable());
        assertEquals(1, page.getTotalCount());
        assertTrue(page.getPageItems().isEmpty());
        verify(jdbcTemplate, never()).query(anyString(), any(Object[].class), same(rowMapper));
    }
    
    @Test
    void testFetchPageRejectsInvalidInputAndNullCount() {
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPage("countSql", "selectSql", new Object[0], 0, 10, rowMapper));
        when(jdbcTemplate.queryForObject(eq("countSql"), any(Object[].class),
            eq(Integer.class))).thenReturn(null);
        
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPage("countSql", "selectSql", new Object[0], 1, 10, rowMapper));
    }
    
    @Test
    void testFetchPageLimitWithoutCountArgs() {
        when(jdbcTemplate.queryForObject("countSql", Integer.class)).thenReturn(3);
        when(jdbcTemplate.query(anyString(), any(Object[].class), same(rowMapper)))
            .thenReturn(Collections.singletonList("one"));
        
        Page<String> page =
            helper.fetchPageLimit("countSql", "selectSql", new Object[] {"nacos"}, 1, 2,
                rowMapper);
        
        assertEquals(1, page.getPageNumber());
        assertEquals(2, page.getPagesAvailable());
        assertEquals(Collections.singletonList("one"), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitWithoutCountArgsRejectsInvalidAndNullCount() {
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPageLimit("countSql", "selectSql", new Object[0], 1, 0,
                rowMapper));
        when(jdbcTemplate.queryForObject("countSql", Integer.class)).thenReturn(null);
        
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPageLimit("countSql", "selectSql", new Object[0], 1, 10,
                rowMapper));
    }
    
    @Test
    void testFetchPageLimitWithoutCountArgsReturnsEmptyWhenPageBeyondCount() {
        when(jdbcTemplate.queryForObject("countSql", Integer.class)).thenReturn(1);
        
        Page<String> page =
            helper.fetchPageLimit("countSql", "selectSql", new Object[0], 2, 10, rowMapper);
        
        assertEquals(2, page.getPageNumber());
        assertEquals(1, page.getPagesAvailable());
        assertEquals(1, page.getTotalCount());
        assertEquals(Collections.emptyList(), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitWithSeparateCountAndFetchArgs() {
        when(jdbcTemplate.queryForObject(eq("countSql"), any(Object[].class),
            eq(Integer.class))).thenReturn(2);
        when(jdbcTemplate.query(anyString(), any(Object[].class), same(rowMapper)))
            .thenReturn(Collections.singletonList("one"));
        
        Page<String> page = helper.fetchPageLimit("countSql", new Object[] {"tenant"},
            "selectSql", new Object[] {"user"}, 1, 2, rowMapper);
        
        assertEquals(2, page.getTotalCount());
        assertEquals(Collections.singletonList("one"), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitWithSeparateCountArgsRejectsInvalidAndNullCount() {
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPageLimit("countSql", new Object[0], "selectSql", new Object[0],
                0, 10, rowMapper));
        when(jdbcTemplate.queryForObject(eq("countSql"), any(Object[].class),
            eq(Integer.class))).thenReturn(null);
        
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPageLimit("countSql", new Object[0], "selectSql", new Object[0],
                1, 10, rowMapper));
    }
    
    @Test
    void testFetchPageLimitWithSeparateCountArgsReturnsEmptyWhenPageBeyondCount() {
        when(jdbcTemplate.queryForObject(eq("countSql"), any(Object[].class),
            eq(Integer.class))).thenReturn(1);
        
        Page<String> page = helper.fetchPageLimit("countSql", new Object[0], "selectSql",
            new Object[0], 2, 10, rowMapper);
        
        assertEquals(2, page.getPageNumber());
        assertEquals(1, page.getPagesAvailable());
        assertEquals(1, page.getTotalCount());
        assertEquals(Collections.emptyList(), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitWithMapperResult() {
        when(jdbcTemplate.queryForObject(eq("countSql"), any(Object[].class),
            eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(Object[].class), same(rowMapper)))
            .thenReturn(Collections.singletonList("one"));
        MapperResult count = new MapperResult("countSql", Collections.singletonList("tenant"));
        MapperResult fetch = new MapperResult("selectSql", Collections.singletonList("user"));
        
        Page page = helper.fetchPageLimit(count, fetch, 1, 10, rowMapper);
        
        assertEquals(1, page.getTotalCount());
        assertEquals(Collections.singletonList("one"), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitFetchOnly() {
        when(jdbcTemplate.query(anyString(), any(Object[].class), same(rowMapper)))
            .thenReturn(Collections.singletonList("one"));
        
        Page<String> page =
            helper.fetchPageLimit("selectSql", new Object[] {"user"}, 1, 10, rowMapper);
        
        assertEquals(Collections.singletonList("one"), page.getPageItems());
    }
    
    @Test
    void testFetchPageLimitFetchOnlyRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
            () -> helper.fetchPageLimit("selectSql", new Object[0], 0, 10, rowMapper));
    }
    
    @Test
    void testUpdateLimitAndUpdateLimitWithResponseCleanContext() {
        when(jdbcTemplate.update("updateSql", new Object[] {"user"})).thenReturn(1);
        
        helper.updateLimit("updateSql", new Object[] {"user"});
        int rows = helper.updateLimitWithResponse("updateSql", new Object[] {"user"});
        
        assertEquals(1, rows);
        verify(jdbcTemplate, times(2)).update("updateSql", new Object[] {"user"});
    }
    
    @Test
    void testDefaultHandlerAdapterForUnknownDataSourceType() {
        TestableAuthExternalPaginationHelper<String> testableHelper =
            new TestableAuthExternalPaginationHelper<>(jdbcTemplate, "unknown");
        
        PageHandlerAdapter adapter = testableHelper.exposedGetHandlerAdapter("unknown");
        
        assertTrue(adapter instanceof DefaultPageHandlerAdapter);
    }
    
    private static final class TestableAuthExternalPaginationHelper<E>
        extends AuthExternalPaginationHelperImpl<E> {
        
        TestableAuthExternalPaginationHelper(JdbcTemplate jdbcTemplate, String dataSourceType) {
            super(jdbcTemplate, dataSourceType);
        }
        
        PageHandlerAdapter exposedGetHandlerAdapter(String dataSourceType) {
            return getHandlerAdapter(dataSourceType);
        }
    }
}
