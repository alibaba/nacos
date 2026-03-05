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

package com.alibaba.nacos.core.distributed.raft.utils;

import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailoverClosureImplTest {
    
    @Test
    void testSetResponseAndRunWithOkStatus() throws Exception {
        CompletableFuture<Response> future = new CompletableFuture<>();
        FailoverClosureImpl closure = new FailoverClosureImpl(future);
        Response response = Response.getDefaultInstance();
        closure.setResponse(response);
        closure.run(Status.OK());
        assertTrue(future.isDone());
        assertSame(response, future.get());
    }
    
    @Test
    void testRunWithFailedStatusWithoutThrowable() {
        CompletableFuture<Response> future = new CompletableFuture<>();
        FailoverClosureImpl closure = new FailoverClosureImpl(future);
        closure.run(new Status(RaftError.UNKNOWN, "error"));
        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get());
        assertTrue(ex.getCause() instanceof ConsistencyException);
        assertEquals("operation failure", ex.getCause().getMessage());
    }
    
    @Test
    void testRunWithFailedStatusWithThrowable() {
        CompletableFuture<Response> future = new CompletableFuture<>();
        FailoverClosureImpl closure = new FailoverClosureImpl(future);
        RuntimeException cause = new RuntimeException("custom error");
        closure.setThrowable(cause);
        closure.run(new Status(RaftError.UNKNOWN, "error"));
        assertTrue(future.isCompletedExceptionally());
        try {
            future.get();
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof ConsistencyException);
            assertEquals("custom error", e.getCause().getMessage());
        }
    }
    
    @Test
    void testSetResponseAndSetThrowable() throws Exception {
        CompletableFuture<Response> future = new CompletableFuture<>();
        FailoverClosureImpl closure = new FailoverClosureImpl(future);
        Response response = Response.getDefaultInstance();
        closure.setResponse(response);
        closure.setThrowable(new RuntimeException("ignored when status is ok"));
        closure.run(Status.OK());
        assertTrue(future.isDone());
        assertSame(response, future.get());
    }
}
