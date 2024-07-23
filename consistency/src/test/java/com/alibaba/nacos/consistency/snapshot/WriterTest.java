/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.consistency.snapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Writer} unit test.
 *
 * @author chenglu
 * @date 2021-07-28 18:50
 */
class WriterTest {
    
    private Writer writer;
    
    @BeforeEach
    void setUp() {
        writer = new Writer("test");
    }
    
    @Test
    void test() {
        assertEquals("test", writer.getPath());
        
        assertTrue(writer.addFile("a"));
        
        assertTrue(writer.addFile("b", new LocalFileMeta()));
        
        assertEquals(2, writer.listFiles().size());
        
        assertTrue(writer.removeFile("a"));
        
        assertEquals(1, writer.listFiles().size());
    }
}
