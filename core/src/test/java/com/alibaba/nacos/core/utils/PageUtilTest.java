/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageUtilTest {
    
    @Test
    void subPageListWhenEmptyReturnsEmpty() {
        List<String> empty = Collections.emptyList();
        List<String> result = PageUtil.subPageList(empty, 1, 10);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void subPageListFirstPage() {
        List<String> source = Arrays.asList("a", "b", "c", "d", "e");
        List<String> result = PageUtil.subPageList(source, 1, 2);
        assertEquals(2, result.size());
        assertEquals("a", result.get(0));
        assertEquals("b", result.get(1));
    }
    
    @Test
    void subPageListSecondPage() {
        List<String> source = Arrays.asList("a", "b", "c", "d", "e");
        List<String> result = PageUtil.subPageList(source, 2, 2);
        assertEquals(2, result.size());
        assertEquals("c", result.get(0));
        assertEquals("d", result.get(1));
    }
    
    @Test
    void subPageListLastPagePartial() {
        List<String> source = Arrays.asList("a", "b", "c", "d", "e");
        List<String> result = PageUtil.subPageList(source, 3, 2);
        assertEquals(1, result.size());
        assertEquals("e", result.get(0));
    }
    
    @Test
    void subPageListPageBeyondSizeReturnsEmpty() {
        List<String> source = Arrays.asList("a", "b", "c");
        List<String> result = PageUtil.subPageList(source, 5, 2);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void subPageWhenEmptyReturnsEmptyPage() {
        List<String> empty = Collections.emptyList();
        Page<String> result = PageUtil.subPage(empty, 1, 10);
        assertEquals(1, result.getPageNumber());
        assertTrue(result.getPageItems().isEmpty());
    }
    
    @Test
    void subPageFirstPage() {
        List<String> source = Arrays.asList("a", "b", "c", "d", "e");
        Page<String> result = PageUtil.subPage(source, 1, 2);
        assertEquals(1, result.getPageNumber());
        assertEquals(5, result.getTotalCount());
        assertEquals(3, result.getPagesAvailable());
        assertEquals(2, result.getPageItems().size());
        assertEquals("a", result.getPageItems().get(0));
        assertEquals("b", result.getPageItems().get(1));
    }
    
    @Test
    void subPageLastPagePartial() {
        List<String> source = Arrays.asList("a", "b", "c", "d", "e");
        Page<String> result = PageUtil.subPage(source, 3, 2);
        assertEquals(3, result.getPageNumber());
        assertEquals(5, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        assertEquals("e", result.getPageItems().get(0));
    }
    
    @Test
    void subPagePageBeyondSizeReturnsEmptyItems() {
        List<String> source = Arrays.asList("a", "b", "c");
        Page<String> result = PageUtil.subPage(source, 5, 2);
        assertEquals(5, result.getPageNumber());
        assertEquals(3, result.getTotalCount());
        assertTrue(result.getPageItems().isEmpty());
    }
    
    @Test
    void subPageListWithInvalidPageNumber() {
        List<String> source = Arrays.asList("a", "b", "c");
        List<String> result = PageUtil.subPageList(source, 0, 2);
        assertEquals(2, result.size());
        assertEquals("a", result.get(0));
        assertEquals("b", result.get(1));
    }
    
    @Test
    void subPageExactlyOneFullPage() {
        List<String> source = Arrays.asList("a", "b");
        Page<String> result = PageUtil.subPage(source, 1, 2);
        assertEquals(2, result.getPageItems().size());
        assertEquals(2, result.getTotalCount());
        assertEquals(2, result.getPagesAvailable());
    }
    
    @Test
    void subPageListWhenStartEqualsSizeReturnsEmpty() {
        List<String> source = Arrays.asList("a", "b");
        List<String> result = PageUtil.subPageList(source, 2, 2);
        assertTrue(result.isEmpty());
    }
}
