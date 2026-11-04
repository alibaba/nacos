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

package com.alibaba.nacos.core.distributed.raft.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Regression coverage for bounded control requests, cached diagnostics and failed cleanup. */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class FileRaftProcessDiagnosticsTest {
    
    @Test
    void testDiagnosticsUseCachedStateWithoutControlRequests() throws Exception {
        Process child = mock(Process.class);
        when(child.isAlive()).thenReturn(true);
        Socket socket = mock(Socket.class);
        InputStream input = spy(new ByteArrayInputStream(encoded("true|12|12|[1, 2]|0")));
        FileRaftProcessTest.Member member = member(child, socket, input,
            new ByteArrayOutputStream());
        assertEquals("true|12|12|[1, 2]|0", member.request("STATE"));
        clearInvocations(socket, input);
        
        String diagnostics = member.diagnostics();
        
        assertTrue(diagnostics.contains("node.log"));
        assertTrue(diagnostics.contains("alive=true"));
        assertTrue(diagnostics.contains("lastState=true|12|12|[1, 2]|0"));
        verifyNoInteractions(socket, input);
    }
    
    @Test
    void testExpiredDeadlineDoesNotSendControlRequest() throws Exception {
        Socket socket = mock(Socket.class);
        InputStream input = mock(InputStream.class);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FileRaftProcessTest.Member member = member(mock(Process.class), socket, input, output);
        
        assertThrows(SocketTimeoutException.class,
            () -> member.request("STATE", System.nanoTime() - 1));
        
        assertEquals(0, output.size());
        verifyNoInteractions(input);
        verify(socket).close();
    }
    
    @Test
    void testResponseReadsUseRemainingWaitBudget() throws Exception {
        Socket socket = mock(Socket.class);
        AtomicLong clock = new AtomicLong(TimeUnit.SECONDS.toNanos(10));
        FileRaftProcessTest.Member member = member(mock(Process.class), socket,
            timedInput(encoded("false|12|12|[1, 2]|0"), clock, 1),
            new ByteArrayOutputStream(), clock::get);
        clearInvocations(socket);
        
        assertEquals("false|12|12|[1, 2]|0",
            member.request("STATE", clock.get() + TimeUnit.SECONDS.toNanos(5)));
        
        ArgumentCaptor<Integer> timeout = ArgumentCaptor.forClass(Integer.class);
        verify(socket, atLeastOnce()).setSoTimeout(timeout.capture());
        // Sending, the two UTF length bytes and the payload share one deadline.
        assertEquals(List.of(5_000, 5_000, 4_000, 3_000), timeout.getAllValues());
        verify(socket, org.mockito.Mockito.never()).close();
    }
    
    @Test
    void testPartialResponseCannotRenewExpiredWaitBudget() throws Exception {
        Socket socket = mock(Socket.class);
        AtomicLong clock = new AtomicLong(TimeUnit.SECONDS.toNanos(10));
        byte[] response = encoded("false|12|12|[1, 2]|0");
        InputStream input = timedInput(response, clock, 5);
        FileRaftProcessTest.Member member = member(mock(Process.class), socket, input,
            new ByteArrayOutputStream(), clock::get);
        
        SocketTimeoutException failure = assertThrows(SocketTimeoutException.class,
            () -> member.request("STATE", clock.get() + TimeUnit.SECONDS.toNanos(5)));
        
        assertTrue(failure.getMessage().contains("deadline expired"));
        assertEquals(response.length - 1, input.available(),
            "The next response byte must not be read after the deadline");
        verify(socket).close();
    }
    
    @Test
    void testReadFailureClosesControlSocketAndPreservesCause() throws Exception {
        Socket socket = mock(Socket.class);
        InputStream input = mock(InputStream.class);
        SocketTimeoutException failure = new SocketTimeoutException("stalled control channel");
        when(input.read()).thenThrow(failure);
        FileRaftProcessTest.Member member = member(mock(Process.class), socket, input,
            new ByteArrayOutputStream());
        
        assertSame(failure, assertThrows(SocketTimeoutException.class,
            () -> member.request("STATE")));
        verify(socket).close();
        clearInvocations(socket, input);
        assertTrue(member.diagnostics().contains("lastState=<not sampled>"));
        verifyNoInteractions(socket, input);
    }
    
    @Test
    void testFailedStopStillTerminatesAndClosesEveryMember() throws Exception {
        FileRaftProcessTest.Member[] members = new FileRaftProcessTest.Member[3];
        Process[] children = new Process[3];
        Socket[] sockets = new Socket[3];
        IOException cleanupFailure = new IOException("first socket close failed");
        for (int i = 0; i < members.length; i++) {
            children[i] = mock(Process.class);
            when(children[i].waitFor(10, TimeUnit.SECONDS)).thenReturn(true);
            sockets[i] = mock(Socket.class);
            members[i] = member(children[i], sockets[i],
                new ByteArrayInputStream(encoded("STOP_FAILED")), new ByteArrayOutputStream());
        }
        doThrow(cleanupFailure).when(sockets[0]).close();
        
        AssertionError failure = assertThrows(AssertionError.class,
            () -> FileRaftProcessTest.stopMembers(members, Path.of("cluster")));
        
        assertTrue(failure.getCause() instanceof AssertionError);
        assertTrue(Arrays.asList(failure.getSuppressed()).contains(cleanupFailure));
        for (int i = 0; i < members.length; i++) {
            verify(children[i]).destroyForcibly();
            verify(children[i]).waitFor(10, TimeUnit.SECONDS);
            verify(sockets[i]).close();
            assertNull(members[i]);
        }
    }
    
    private static FileRaftProcessTest.Member member(Process child, Socket socket,
        InputStream input, ByteArrayOutputStream output) throws IOException {
        return member(child, socket, input, output, System::nanoTime);
    }
    
    private static FileRaftProcessTest.Member member(Process child, Socket socket,
        InputStream input, ByteArrayOutputStream output, LongSupplier clock) throws IOException {
        when(socket.getInputStream()).thenReturn(input);
        when(socket.getOutputStream()).thenReturn(output);
        return new FileRaftProcessTest.Member(child, socket, Path.of("node.log"), clock);
    }
    
    private static InputStream timedInput(byte[] bytes, AtomicLong clock, long secondsPerRead) {
        return new ByteArrayInputStream(bytes) {
            
            @Override
            public synchronized int read() {
                int result = super.read();
                clock.addAndGet(TimeUnit.SECONDS.toNanos(secondsPerRead));
                return result;
            }
            
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                int result = super.read(buffer, offset, length);
                clock.addAndGet(TimeUnit.SECONDS.toNanos(secondsPerRead));
                return result;
            }
        };
    }
    
    private static byte[] encoded(String response) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(response);
        }
        return bytes.toByteArray();
    }
}
