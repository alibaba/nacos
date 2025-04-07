/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.repository.embedded;

import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddedPaginationHelperImplTest {
    
    private static final String QUERY_SQL = "SELECT * FROM config_info LIMIT 1";
    
    private static final String QUERY_COUNT_SQL = "SELECT count(*) FROM config_info";
    
    @Mock
    DatabaseOperate databaseOperate;
    
    @Mock
    RowMapper rowMapper;
    
    EmbeddedPaginationHelperImpl<Object> embeddedPaginationHelper;
    
    @BeforeEach
    void setUp() {
        embeddedPaginationHelper = new EmbeddedPaginationHelperImpl<>(databaseOperate);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void testFetchPageWithIllegalPageInfo() {
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPage("", "", new Object[] {}, 0, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPage("", "", new Object[] {}, 1, 0, null));
    }
    
    @Test
    void testFetchPageWithoutResult() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPage(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 1, 1, null));
    }
    
    @Test
    void testFetchPageOnePage() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(1);
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(
                Collections.singletonList(new Object()));
        Page<Object> actual = embeddedPaginationHelper.fetchPage(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 1, 1,
                rowMapper);
        assertEquals(1, actual.getTotalCount());
        assertEquals(1, actual.getPageNumber());
        assertEquals(1, actual.getPagesAvailable());
        assertEquals(1, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageMorePageFull() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(2);
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(
                Collections.singletonList(new Object()));
        Page<Object> actual = embeddedPaginationHelper.fetchPage(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 1, 1,
                rowMapper);
        assertEquals(2, actual.getTotalCount());
        assertEquals(1, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(1, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageMorePageNotFull() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(3);
        List<Object> pageItems = new LinkedList<>();
        pageItems.add(new Object());
        pageItems.add(new Object());
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(pageItems);
        Page<Object> actual = embeddedPaginationHelper.fetchPage(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 1, 2,
                rowMapper);
        assertEquals(3, actual.getTotalCount());
        assertEquals(1, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(2, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageMorePageNextPage() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(3);
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(
                Collections.singletonList(new Object()));
        Page<Object> actual = embeddedPaginationHelper.fetchPage(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 2, 2,
                rowMapper);
        assertEquals(3, actual.getTotalCount());
        assertEquals(2, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(1, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageMoreThanItemCount() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(3);
        Page<Object> actual = embeddedPaginationHelper.fetchPage(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 3, 2,
                rowMapper);
        assertEquals(3, actual.getTotalCount());
        assertEquals(3, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(0, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitWithIllegalPageInfo() {
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPageLimit("", "", new Object[] {}, 0, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPageLimit("", "", new Object[] {}, 1, 0, null));
    }
    
    @Test
    void testFetchPageLimitWithoutResult() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, Integer.class)).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPageLimit(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 1, 1, null));
    }
    
    @Test
    void testFetchPageLimitOnePage() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, Integer.class)).thenReturn(1);
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(
                Collections.singletonList(new Object()));
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 1, 1,
                rowMapper);
        assertEquals(1, actual.getTotalCount());
        assertEquals(1, actual.getPageNumber());
        assertEquals(1, actual.getPagesAvailable());
        assertEquals(1, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitMorePageFull() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, Integer.class)).thenReturn(2);
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(
                Collections.singletonList(new Object()));
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 1, 1,
                rowMapper);
        assertEquals(2, actual.getTotalCount());
        assertEquals(1, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(1, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitMorePageNotFull() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, Integer.class)).thenReturn(3);
        List<Object> pageItems = new LinkedList<>();
        pageItems.add(new Object());
        pageItems.add(new Object());
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(pageItems);
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 1, 2,
                rowMapper);
        assertEquals(3, actual.getTotalCount());
        assertEquals(1, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(2, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitMorePageNextPage() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, Integer.class)).thenReturn(3);
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(
                Collections.singletonList(new Object()));
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 2, 2,
                rowMapper);
        assertEquals(3, actual.getTotalCount());
        assertEquals(2, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(1, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitMoreThanItemCount() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, Integer.class)).thenReturn(3);
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(QUERY_COUNT_SQL, QUERY_SQL, new Object[] {}, 3, 2,
                rowMapper);
        assertEquals(3, actual.getTotalCount());
        assertEquals(3, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(0, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitWithPluginWithIllegalPageInfo() {
        MapperResult countMapper = new MapperResult(QUERY_COUNT_SQL, new ArrayList<>());
        MapperResult queryMapper = new MapperResult(QUERY_SQL, new ArrayList<>());
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPageLimit(countMapper, queryMapper, 0, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPageLimit(countMapper, queryMapper, 1, 0, null));
    }
    
    @Test
    void testFetchPageLimitWithPluginWithoutResult() {
        MapperResult countMapper = new MapperResult(QUERY_COUNT_SQL, new ArrayList<>());
        MapperResult queryMapper = new MapperResult(QUERY_SQL, new ArrayList<>());
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPageLimit(countMapper, queryMapper, 1, 1, null));
    }
    
    @Test
    void testFetchPageLimitWithPluginPageOnePage() {
        MapperResult countMapper = new MapperResult(QUERY_COUNT_SQL, new ArrayList<>());
        MapperResult queryMapper = new MapperResult(QUERY_SQL, new ArrayList<>());
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(1);
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(
                Collections.singletonList(new Object()));
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(countMapper, queryMapper, 1, 1, rowMapper);
        assertEquals(1, actual.getTotalCount());
        assertEquals(1, actual.getPageNumber());
        assertEquals(1, actual.getPagesAvailable());
        assertEquals(1, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitWithPluginMorePageFull() {
        MapperResult countMapper = new MapperResult(QUERY_COUNT_SQL, new ArrayList<>());
        MapperResult queryMapper = new MapperResult(QUERY_SQL, new ArrayList<>());
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(2);
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(
                Collections.singletonList(new Object()));
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(countMapper, queryMapper, 1, 1, rowMapper);
        assertEquals(2, actual.getTotalCount());
        assertEquals(1, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(1, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitWithPluginMorePageNotFull() {
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(3);
        List<Object> pageItems = new LinkedList<>();
        pageItems.add(new Object());
        pageItems.add(new Object());
        MapperResult countMapper = new MapperResult(QUERY_COUNT_SQL, new ArrayList<>());
        MapperResult queryMapper = new MapperResult(QUERY_SQL, new ArrayList<>());
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(pageItems);
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(countMapper, queryMapper, 1, 2, rowMapper);
        assertEquals(3, actual.getTotalCount());
        assertEquals(1, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(2, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitWithPluginMorePageNextPage() {
        MapperResult countMapper = new MapperResult(QUERY_COUNT_SQL, new ArrayList<>());
        MapperResult queryMapper = new MapperResult(QUERY_SQL, new ArrayList<>());
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(3);
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(
                Collections.singletonList(new Object()));
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(countMapper, queryMapper, 2, 2, rowMapper);
        assertEquals(3, actual.getTotalCount());
        assertEquals(2, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(1, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitWithPluginMoreThanItemCount() {
        MapperResult countMapper = new MapperResult(QUERY_COUNT_SQL, new ArrayList<>());
        MapperResult queryMapper = new MapperResult(QUERY_SQL, new ArrayList<>());
        when(databaseOperate.queryOne(QUERY_COUNT_SQL, new Object[] {}, Integer.class)).thenReturn(3);
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(countMapper, queryMapper, 3, 2, rowMapper);
        assertEquals(3, actual.getTotalCount());
        assertEquals(3, actual.getPageNumber());
        assertEquals(2, actual.getPagesAvailable());
        assertEquals(0, actual.getPageItems().size());
    }
    
    @Test
    void testFetchPageLimitSimpleWithIllegalPageInfo() {
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPageLimit(QUERY_SQL, new Object[] {}, 0, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> embeddedPaginationHelper.fetchPageLimit(QUERY_SQL, new Object[] {}, 1, 0, null));
    }
    
    @Test
    void testFetchPageLimitSimpleWithData() {
        List<Object> pageItems = new LinkedList<>();
        pageItems.add(new Object());
        pageItems.add(new Object());
        pageItems.add(new Object());
        when(databaseOperate.queryMany(QUERY_SQL, new Object[] {}, rowMapper)).thenReturn(pageItems);
        Page<Object> actual = embeddedPaginationHelper.fetchPageLimit(QUERY_SQL, new Object[] {}, 3, 1, rowMapper);
        assertEquals(0, actual.getTotalCount());
        assertEquals(0, actual.getPageNumber());
        assertEquals(0, actual.getPagesAvailable());
        assertEquals(3, actual.getPageItems().size());
    }
    
    @Test
    void updateLimit() {
        Object[] args = new Object[] {};
        embeddedPaginationHelper.updateLimit(QUERY_SQL, args);
        verify(databaseOperate).update(argThat(modifyRequests -> {
            if (modifyRequests.size() != 1) {
                return false;
            }
            if (!QUERY_SQL.equals(modifyRequests.get(0).getSql())) {
                return false;
            }
            return 0 == modifyRequests.get(0).getArgs().length;
        }));
    }
}