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

package com.alibaba.nacos.plugin.auth.impl.oracle.persistence;

import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OracleAuthPaginationHelperTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private RowMapper<Object> rowMapper;
    
    private OracleAuthPaginationHelper<Object> oracleAuthPaginationHelper;
    
    @BeforeEach
    void setUp() {
        oracleAuthPaginationHelper = new OracleAuthPaginationHelper<>(jdbcTemplate);
    }
    
    @Test
    void testFetchPageAppendsOffsetAndFetchArgs() {
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Integer.class)))
            .thenReturn(25);
        when(jdbcTemplate.query(any(String.class), any(Object[].class), any(RowMapper.class)))
            .thenReturn(Collections.singletonList("row"));
        
        Page<Object> page = oracleAuthPaginationHelper.fetchPage("SELECT count(*) FROM roles",
            "SELECT role FROM roles", new Object[] {"a"}, 2, 10, rowMapper);
        
        assertEquals(25, page.getTotalCount());
        assertEquals(3, page.getPagesAvailable());
        assertEquals(2, page.getPageNumber());
        assertEquals(Collections.singletonList("row"), page.getPageItems());
        
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), argsCaptor.capture(), eq(rowMapper));
        assertEquals("SELECT role FROM roles OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            sqlCaptor.getValue());
        assertEquals(3, argsCaptor.getValue().length);
        assertEquals("a", argsCaptor.getValue()[0]);
        assertEquals(10, argsCaptor.getValue()[1]);
        assertEquals(10, argsCaptor.getValue()[2]);
    }
    
    @Test
    void testFetchPageBeyondPageCountReturnsEmptyItems() {
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Integer.class)))
            .thenReturn(25);
        
        Page<Object> page = oracleAuthPaginationHelper.fetchPage("count", "fetch",
            new Object[] {}, 4, 10, rowMapper);
        
        assertEquals(25, page.getTotalCount());
        assertTrue(page.getPageItems().isEmpty());
        verify(jdbcTemplate, org.mockito.Mockito.never())
            .query(any(String.class), any(Object[].class), any(RowMapper.class));
    }
    
    @Test
    void testFetchPageRejectsNonPositiveArguments() {
        assertThrows(IllegalArgumentException.class,
            () -> oracleAuthPaginationHelper.fetchPage("count", "fetch", new Object[] {}, 0, 10,
                rowMapper));
        assertThrows(IllegalArgumentException.class,
            () -> oracleAuthPaginationHelper.fetchPage("count", "fetch", new Object[] {}, 1, 0,
                rowMapper));
    }
    
    @Test
    void testFetchPageRejectsLastMaxId() {
        assertThrows(UnsupportedOperationException.class,
            () -> oracleAuthPaginationHelper.fetchPage("count", "fetch", new Object[] {}, 1, 10,
                1L, rowMapper));
    }
    
    @Test
    void testFetchPageLimitIsNotSupported() {
        assertThrows(UnsupportedOperationException.class,
            () -> oracleAuthPaginationHelper.fetchPageLimit("count", "fetch", new Object[] {}, 1,
                10, rowMapper));
        assertThrows(UnsupportedOperationException.class,
            () -> oracleAuthPaginationHelper.fetchPageLimit("count", new Object[] {}, "fetch",
                new Object[] {}, 1, 10, rowMapper));
        assertThrows(UnsupportedOperationException.class,
            () -> oracleAuthPaginationHelper.fetchPageLimit("fetch", new Object[] {}, 1, 10,
                rowMapper));
    }
    
    @Test
    void testUpdateLimitDelegatesToUpdate() {
        oracleAuthPaginationHelper.updateLimit("UPDATE users SET enabled = ?", new Object[] {1});
        verify(jdbcTemplate).update("UPDATE users SET enabled = ?", new Object[] {1});
    }
}
