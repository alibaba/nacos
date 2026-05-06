/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed.raft.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NoSuchRaftGroupExceptionTest {
    
    @Test
    void testDefaultConstructor() {
        NoSuchRaftGroupException ex = new NoSuchRaftGroupException();
        assertNull(ex.getMessage());
        assertNull(ex.getCause());
    }
    
    @Test
    void testMessageConstructor() {
        NoSuchRaftGroupException ex = new NoSuchRaftGroupException("no such group");
        assertEquals("no such group", ex.getMessage());
    }
    
    @Test
    void testMessageAndCauseConstructor() {
        Throwable cause = new IllegalArgumentException("cause");
        NoSuchRaftGroupException ex = new NoSuchRaftGroupException("no such group", cause);
        assertEquals("no such group", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
    
    @Test
    void testCauseOnlyConstructor() {
        Throwable cause = new RuntimeException("cause");
        NoSuchRaftGroupException ex = new NoSuchRaftGroupException(cause);
        assertSame(cause, ex.getCause());
    }
    
    @Test
    void testFullConstructor() {
        Throwable cause = new RuntimeException("cause");
        NoSuchRaftGroupException ex = new NoSuchRaftGroupException("msg", cause, true, true);
        assertEquals("msg", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
