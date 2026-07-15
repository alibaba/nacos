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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.consistency.entity.Response;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;
import com.google.protobuf.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NacosClosureTest {
    
    @Mock
    private Closure closure;
    
    @Test
    void testRunWithOkStatusInvokesClosureWithNacosStatus() {
        Message message = Response.getDefaultInstance();
        NacosClosure nacosClosure = new NacosClosure(message, closure);
        assertSame(message, nacosClosure.getMessage());
        
        nacosClosure.run(Status.OK());
        
        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(closure).run(statusCaptor.capture());
        Status captured = statusCaptor.getValue();
        assertTrue(captured.isOk());
        assertNotNull(captured);
        assertTrue(captured instanceof NacosClosure.NacosStatus);
    }
    
    @Test
    void testRunWithFailedStatusInvokesClosureWithFailedNacosStatus() {
        NacosClosure nacosClosure = new NacosClosure(Response.getDefaultInstance(), closure);
        Status failed = new Status(RaftError.UNKNOWN, "raft error");
        nacosClosure.run(failed);
        
        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(closure).run(statusCaptor.capture());
        NacosClosure.NacosStatus captured = (NacosClosure.NacosStatus) statusCaptor.getValue();
        assertFalse(captured.isOk());
        assertEquals(failed.getCode(), captured.getCode());
        assertEquals(failed.getErrorMsg(), captured.getErrorMsg());
    }
    
    @Test
    void testSetResponseBeforeRunPassedToNacosStatus() {
        Message message = Response.getDefaultInstance();
        Response customResponse = Response.newBuilder().setSuccess(true).build();
        final NacosClosure.NacosStatus[] capturedRef = new NacosClosure.NacosStatus[1];
        Closure capturingClosure = status -> capturedRef[0] = (NacosClosure.NacosStatus) status;
        
        NacosClosure nacosClosure = new NacosClosure(message, capturingClosure);
        nacosClosure.setResponse(customResponse);
        nacosClosure.run(Status.OK());
        
        assertNotNull(capturedRef[0]);
        assertSame(customResponse, capturedRef[0].getResponse());
    }
    
    @Test
    void testSetThrowableBeforeRunPassedToNacosStatus() {
        Throwable throwable = new RuntimeException("test error");
        final NacosClosure.NacosStatus[] capturedRef = new NacosClosure.NacosStatus[1];
        Closure capturingClosure = status -> capturedRef[0] = (NacosClosure.NacosStatus) status;
        
        NacosClosure nacosClosure =
            new NacosClosure(Response.getDefaultInstance(), capturingClosure);
        nacosClosure.setThrowable(throwable);
        nacosClosure.run(Status.OK());
        
        assertNotNull(capturedRef[0]);
        assertSame(throwable, capturedRef[0].getThrowable());
    }
    
    @Test
    void testNacosStatusCopy() {
        final NacosClosure.NacosStatus[] capturedRef = new NacosClosure.NacosStatus[1];
        Closure capturingClosure = status -> capturedRef[0] = (NacosClosure.NacosStatus) status;
        NacosClosure nacosClosure =
            new NacosClosure(Response.getDefaultInstance(), capturingClosure);
        Response resp = Response.newBuilder().setSuccess(true).build();
        nacosClosure.setResponse(resp);
        nacosClosure.run(Status.OK());
        
        NacosClosure.NacosStatus copy = (NacosClosure.NacosStatus) capturedRef[0].copy();
        assertNotNull(copy);
        assertSame(resp, copy.getResponse());
    }
    
    @Test
    void testNacosStatusReset() {
        final NacosClosure.NacosStatus[] capturedRef = new NacosClosure.NacosStatus[1];
        Closure capturingClosure = status -> capturedRef[0] = (NacosClosure.NacosStatus) status;
        NacosClosure nacosClosure =
            new NacosClosure(Response.getDefaultInstance(), capturingClosure);
        nacosClosure.run(new Status(RaftError.UNKNOWN, "error"));
        assertNotNull(capturedRef[0]);
        assertFalse(capturedRef[0].isOk());
        capturedRef[0].reset();
        assertTrue(capturedRef[0].isOk());
    }
    
    @Test
    void testNacosStatusSetCodeGetCode() {
        final NacosClosure.NacosStatus[] capturedRef = new NacosClosure.NacosStatus[1];
        Closure capturingClosure = status -> capturedRef[0] = (NacosClosure.NacosStatus) status;
        NacosClosure nacosClosure =
            new NacosClosure(Response.getDefaultInstance(), capturingClosure);
        nacosClosure.run(new Status(-100, "custom"));
        capturedRef[0].setCode(200);
        assertEquals(200, capturedRef[0].getCode());
    }
    
    @Test
    void testNacosStatusGetRaftErrorAndSetError() {
        final NacosClosure.NacosStatus[] capturedRef = new NacosClosure.NacosStatus[1];
        Closure capturingClosure = status -> capturedRef[0] = (NacosClosure.NacosStatus) status;
        NacosClosure nacosClosure =
            new NacosClosure(Response.getDefaultInstance(), capturingClosure);
        nacosClosure.run(new Status(RaftError.UNKNOWN, "unknown"));
        assertNotNull(capturedRef[0].getRaftError());
        assertEquals(RaftError.UNKNOWN, capturedRef[0].getRaftError());
        capturedRef[0].setError(RaftError.EBUSY, "busy %s", "detail");
        assertEquals(RaftError.EBUSY, capturedRef[0].getRaftError());
        capturedRef[0].setError(500, "code %d", 500);
        assertEquals(500, capturedRef[0].getCode());
    }
    
    @Test
    void testNacosStatusSetErrorMsgGetErrorMsg() {
        final NacosClosure.NacosStatus[] capturedRef = new NacosClosure.NacosStatus[1];
        Closure capturingClosure = status -> capturedRef[0] = (NacosClosure.NacosStatus) status;
        NacosClosure nacosClosure =
            new NacosClosure(Response.getDefaultInstance(), capturingClosure);
        nacosClosure.run(Status.OK());
        capturedRef[0].setErrorMsg("custom error msg");
        assertEquals("custom error msg", capturedRef[0].getErrorMsg());
    }
    
    @Test
    void testNacosStatusToString() {
        final NacosClosure.NacosStatus[] capturedRef = new NacosClosure.NacosStatus[1];
        Closure capturingClosure = status -> capturedRef[0] = (NacosClosure.NacosStatus) status;
        NacosClosure nacosClosure =
            new NacosClosure(Response.getDefaultInstance(), capturingClosure);
        nacosClosure.run(new Status(RaftError.UNKNOWN, "err"));
        String s = capturedRef[0].toString();
        assertNotNull(s);
        assertTrue(s.contains("err") || s.length() > 0);
    }
}
