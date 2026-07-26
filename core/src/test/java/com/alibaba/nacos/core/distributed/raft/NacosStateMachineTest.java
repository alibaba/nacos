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

import com.alibaba.nacos.consistency.ProtoMessageUtil;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.LeaderChangeContext;
import com.alipay.sofa.jraft.entity.LocalFileMetaOutter;
import com.alipay.sofa.jraft.entity.NodeId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NacosStateMachineTest {
    
    @Mock
    private JRaftServer server;
    
    @Mock
    private RequestProcessor4CP processor;
    
    @Mock
    private com.alipay.sofa.jraft.Node node;
    
    @Mock
    private NodeId nodeId;
    
    private NacosStateMachine stateMachine;
    
    @BeforeEach
    void setUp() {
        when(processor.group()).thenReturn("test_group");
        when(processor.loadSnapshotOperate()).thenReturn(Collections.emptyList());
        stateMachine = new NacosStateMachine(server, processor);
        Configuration conf = new Configuration();
        conf.addPeer(PeerId.parsePeer("127.0.0.1:7848"));
        RouteTable.getInstance().updateConfiguration("test_group", conf);
    }
    
    @Test
    void testIsLeaderInitiallyFalse() {
        assertFalse(stateMachine.isLeader());
    }
    
    @Test
    void testGetTermInitiallyNegative() {
        assertEquals(-1L, stateMachine.getTerm());
    }
    
    @Test
    void testSetNode() {
        stateMachine.setNode(node);
    }
    
    @Test
    void testOnLeaderStartUpdatesTermAndIsLeader() {
        PeerId peerId = PeerId.parsePeer("127.0.0.1:7848");
        when(node.getNodeId()).thenReturn(nodeId);
        when(nodeId.getPeerId()).thenReturn(peerId);
        when(node.getGroupId()).thenReturn("test_group");
        when(node.isLeader()).thenReturn(true);
        when(node.listPeers()).thenReturn(Collections.singletonList(peerId));
        stateMachine.setNode(node);
        
        stateMachine.onLeaderStart(5L);
        
        assertTrue(stateMachine.isLeader());
        assertEquals(5L, stateMachine.getTerm());
    }
    
    @Test
    void testOnLeaderStopClearsLeader() {
        PeerId peerId = PeerId.parsePeer("127.0.0.1:7848");
        when(node.getNodeId()).thenReturn(nodeId);
        when(nodeId.getPeerId()).thenReturn(peerId);
        when(node.getGroupId()).thenReturn("test_group");
        when(node.isLeader()).thenReturn(true);
        when(node.listPeers()).thenReturn(Collections.singletonList(peerId));
        stateMachine.setNode(node);
        stateMachine.onLeaderStart(1L);
        assertTrue(stateMachine.isLeader());
        
        stateMachine.onLeaderStop(Status.OK());
        
        assertFalse(stateMachine.isLeader());
    }
    
    @Test
    void testOnStartFollowing() {
        PeerId peerId = PeerId.parsePeer("127.0.0.1:7848");
        when(node.getGroupId()).thenReturn("test_group");
        when(node.isLeader()).thenReturn(false);
        stateMachine.setNode(node);
        Configuration conf = new Configuration();
        conf.addPeer(peerId);
        RouteTable.getInstance().updateConfiguration("test_group", conf);
        
        LeaderChangeContext ctx = new LeaderChangeContext(peerId, 3L, Status.OK());
        stateMachine.onStartFollowing(ctx);
        
        assertEquals(3L, stateMachine.getTerm());
    }
    
    @Test
    void testOnConfigurationCommitted() {
        stateMachine.setNode(node);
        Configuration conf = new Configuration();
        conf.addPeer(PeerId.parsePeer("127.0.0.1:7848"));
        stateMachine.onConfigurationCommitted(conf);
        // no exception, event published
    }
    
    @Test
    void testOnError() {
        stateMachine.setNode(node);
        when(node.getGroupId()).thenReturn("test_group");
        when(node.isLeader()).thenReturn(true);
        when(node.listPeers())
            .thenReturn(Collections.singletonList(PeerId.parsePeer("127.0.0.1:7848")));
        RaftException e = org.mockito.Mockito.mock(RaftException.class);
        when(e.toString()).thenReturn("RaftException: raft error");
        stateMachine.onError(e);
        verify(processor).onError(e);
    }
    
    @Test
    void testConstructorWithNullSnapshotOperationSkipsNull() {
        when(processor.loadSnapshotOperate()).thenReturn(Collections.singletonList(null));
        NacosStateMachine sm = new NacosStateMachine(server, processor);
        // no exception; null item is skipped in adapterToJraftSnapshot
    }
    
    @Test
    void testOnSnapshotSaveWithOneOperation(@TempDir Path tempDir) {
        SnapshotOperation userOp = new SnapshotOperation() {
            
            @Override
            public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
                callFinally.accept(true, null);
            }
            
            @Override
            public boolean onSnapshotLoad(Reader reader) {
                return true;
            }
        };
        when(processor.loadSnapshotOperate()).thenReturn(Collections.singletonList(userOp));
        NacosStateMachine sm = new NacosStateMachine(server, processor);
        SnapshotWriter mockWriter = org.mockito.Mockito.mock(SnapshotWriter.class);
        when(mockWriter.getPath()).thenReturn(tempDir.toString());
        when(mockWriter.listFiles()).thenReturn(Collections.emptySet());
        Closure done = org.mockito.Mockito.mock(Closure.class);
        sm.onSnapshotSave(mockWriter, done);
        verify(done).run(any(Status.class));
    }
    
    @Test
    void testOnSnapshotLoadWithOneOperationReturnsTrue() {
        SnapshotOperation loadOp = new SnapshotOperation() {
            
            @Override
            public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
            }
            
            @Override
            public boolean onSnapshotLoad(Reader reader) {
                return true;
            }
        };
        when(processor.loadSnapshotOperate()).thenReturn(Collections.singletonList(loadOp));
        NacosStateMachine sm = new NacosStateMachine(server, processor);
        SnapshotReader mockReader = org.mockito.Mockito.mock(SnapshotReader.class);
        when(mockReader.listFiles()).thenReturn(Collections.emptySet());
        when(mockReader.getPath()).thenReturn("/tmp");
        assertTrue(sm.onSnapshotLoad(mockReader));
    }
    
    @Test
    void testOnSnapshotLoadWhenOperationReturnsFalse() {
        SnapshotOperation loadOp = new SnapshotOperation() {
            
            @Override
            public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
            }
            
            @Override
            public boolean onSnapshotLoad(Reader reader) {
                return false;
            }
        };
        when(processor.loadSnapshotOperate()).thenReturn(Collections.singletonList(loadOp));
        NacosStateMachine sm = new NacosStateMachine(server, processor);
        SnapshotReader mockReader = org.mockito.Mockito.mock(SnapshotReader.class);
        when(mockReader.listFiles()).thenReturn(Collections.emptySet());
        when(mockReader.getPath()).thenReturn("/tmp");
        assertFalse(sm.onSnapshotLoad(mockReader));
    }
    
    @Test
    void testOnSnapshotLoadWhenOperationThrows() {
        SnapshotOperation loadOp = new SnapshotOperation() {
            
            @Override
            public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
            }
            
            @Override
            public boolean onSnapshotLoad(Reader reader) {
                throw new RuntimeException("load failed");
            }
        };
        when(processor.loadSnapshotOperate()).thenReturn(Collections.singletonList(loadOp));
        NacosStateMachine sm = new NacosStateMachine(server, processor);
        SnapshotReader mockReader = org.mockito.Mockito.mock(SnapshotReader.class);
        when(mockReader.listFiles()).thenReturn(Collections.emptySet());
        when(mockReader.getPath()).thenReturn("/tmp");
        assertFalse(sm.onSnapshotLoad(mockReader));
    }
    
    @Test
    void testOnApplyWithWriteRequestAndClosure() {
        NacosClosure nacosClosure = new NacosClosure(WriteRequest.getDefaultInstance(), status -> {
        });
        Iterator iter = org.mockito.Mockito.mock(Iterator.class);
        when(iter.hasNext()).thenReturn(true, false);
        when(iter.done()).thenReturn(nacosClosure);
        when(processor.onApply(any(WriteRequest.class)))
            .thenReturn(Response.newBuilder().setSuccess(true).build());
        
        AtomicReference<Status> runStatus = new AtomicReference<>();
        NacosClosure closureWithCapture =
            new NacosClosure(WriteRequest.getDefaultInstance(), runStatus::set);
        when(iter.done()).thenReturn(closureWithCapture);
        
        stateMachine.onApply(iter);
        
        assertTrue(runStatus.get() != null && runStatus.get().isOk());
        verify(processor).onApply(any(WriteRequest.class));
    }
    
    @Test
    void testOnApplyWithReadRequestAndClosure() {
        AtomicReference<Status> runStatus = new AtomicReference<>();
        NacosClosure closureWithCapture =
            new NacosClosure(ReadRequest.getDefaultInstance(), runStatus::set);
        Iterator iter = org.mockito.Mockito.mock(Iterator.class);
        when(iter.hasNext()).thenReturn(true, false);
        when(iter.done()).thenReturn(closureWithCapture);
        when(processor.onRequest(any(ReadRequest.class)))
            .thenReturn(Response.newBuilder().setSuccess(true).build());
        
        stateMachine.onApply(iter);
        
        assertTrue(runStatus.get() != null && runStatus.get().isOk());
        verify(processor).onRequest(any(ReadRequest.class));
    }
    
    @Test
    void testOnApplyWhenProcessorThrowsCallsSetErrorAndRollback() {
        NacosClosure nacosClosure = new NacosClosure(WriteRequest.getDefaultInstance(), status -> {
        });
        Iterator iter = org.mockito.Mockito.mock(Iterator.class);
        doReturn(true).doReturn(false).when(iter).hasNext();
        when(iter.done()).thenReturn(nacosClosure);
        when(processor.onApply(any(WriteRequest.class)))
            .thenThrow(new RuntimeException("apply failed"));
        
        stateMachine.onApply(iter);
        // Outer catch in onApply catches the exception and calls setErrorAndRollback (line 144-146)
        verify(iter).setErrorAndRollback(anyLong(), any(Status.class));
    }
    
    /**
     * Follower path: iter.done()==null, getData() parses to ReadRequest -> applied++, index++, iter.next(), continue;
     * no processor call.
     */
    @Test
    void testOnApplyFollowerReadRequest() {
        byte[] prefix = new byte[] {(byte) ProtoMessageUtil.REQUEST_TYPE_FIELD_TAG,
            ProtoMessageUtil.REQUEST_TYPE_READ};
        byte[] body = ReadRequest.getDefaultInstance().toByteArray();
        ByteBuffer data =
            ByteBuffer.allocate(prefix.length + body.length).put(prefix).put(body).flip();
        Iterator iter = org.mockito.Mockito.mock(Iterator.class);
        when(iter.hasNext()).thenReturn(true, false);
        when(iter.done()).thenReturn(null);
        when(iter.getData()).thenReturn(data);
        
        stateMachine.onApply(iter);
        
        verify(processor, never()).onRequest(any(ReadRequest.class));
        verify(processor, never()).onApply(any(WriteRequest.class));
        verify(iter).next();
    }
    
    /**
     * Follower path: iter.done()==null, getData() parses to WriteRequest -> processor.onApply, postProcessor(response,
     * null).
     */
    @Test
    void testOnApplyFollowerWriteRequest() {
        byte[] prefix = new byte[] {(byte) ProtoMessageUtil.REQUEST_TYPE_FIELD_TAG,
            ProtoMessageUtil.REQUEST_TYPE_WRITE};
        byte[] body = WriteRequest.getDefaultInstance().toByteArray();
        ByteBuffer data =
            ByteBuffer.allocate(prefix.length + body.length).put(prefix).put(body).flip();
        Iterator iter = org.mockito.Mockito.mock(Iterator.class);
        when(iter.hasNext()).thenReturn(true, false);
        when(iter.done()).thenReturn(null);
        when(iter.getData()).thenReturn(data);
        when(processor.onApply(any(WriteRequest.class)))
            .thenReturn(Response.newBuilder().setSuccess(true).build());
        
        stateMachine.onApply(iter);
        
        verify(processor).onApply(any(WriteRequest.class));
        verify(iter).next();
    }
    
    /**
     * onError without setNode -> allPeers() returns empty list (node==null branch).
     */
    @Test
    void testOnErrorWithoutNodeCoversAllPeersEmpty() {
        RaftException e = org.mockito.Mockito.mock(RaftException.class);
        when(e.toString()).thenReturn("RaftException: error");
        stateMachine.onError(e);
        verify(processor).onError(e);
    }
    
    /**
     * onSnapshotSave when user operation throws -> catch logs and rethrows.
     */
    @Test
    void testOnSnapshotSaveWhenOperationThrows(@TempDir Path tempDir) {
        SnapshotOperation userOp = new SnapshotOperation() {
            
            @Override
            public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
                throw new RuntimeException("save failed");
            }
            
            @Override
            public boolean onSnapshotLoad(Reader reader) {
                return true;
            }
        };
        when(processor.loadSnapshotOperate()).thenReturn(Collections.singletonList(userOp));
        NacosStateMachine sm = new NacosStateMachine(server, processor);
        SnapshotWriter mockWriter = org.mockito.Mockito.mock(SnapshotWriter.class);
        when(mockWriter.getPath()).thenReturn(tempDir.toString());
        Closure done = org.mockito.Mockito.mock(Closure.class);
        
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> sm.onSnapshotSave(mockWriter, done));
        
        verify(done, never()).run(any(Status.class));
    }
    
    /**
     * onSnapshotSave with user op adding file -> adapter calls writer.addFile and done.run(Status.OK()).
     */
    @Test
    void testOnSnapshotSaveWithFilesAdded(@TempDir Path tempDir) {
        SnapshotOperation userOp = new SnapshotOperation() {
            
            @Override
            public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
                writer.addFile("data.dat");
                callFinally.accept(true, null);
            }
            
            @Override
            public boolean onSnapshotLoad(Reader reader) {
                return true;
            }
        };
        when(processor.loadSnapshotOperate()).thenReturn(Collections.singletonList(userOp));
        NacosStateMachine sm = new NacosStateMachine(server, processor);
        SnapshotWriter mockWriter = org.mockito.Mockito.mock(SnapshotWriter.class);
        when(mockWriter.getPath()).thenReturn(tempDir.toString());
        when(mockWriter.addFile(anyString(), any())).thenReturn(true);
        Closure done = org.mockito.Mockito.mock(Closure.class);
        
        sm.onSnapshotSave(mockWriter, done);
        
        verify(mockWriter).addFile(anyString(), any());
        verify(done).run(any(Status.class));
    }
    
    /**
     * onSnapshotLoad with one file and empty userMeta -> bytes.length==0, LocalFileMeta() path.
     */
    @Test
    void testOnSnapshotLoadWithFileEmptyMeta() {
        SnapshotOperation loadOp = new SnapshotOperation() {
            
            @Override
            public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
            }
            
            @Override
            public boolean onSnapshotLoad(Reader reader) {
                assertNotNull(reader.getFileMeta("f1"));
                return reader.listFiles().containsKey("f1");
            }
        };
        when(processor.loadSnapshotOperate()).thenReturn(Collections.singletonList(loadOp));
        NacosStateMachine sm = new NacosStateMachine(server, processor);
        SnapshotReader mockReader = org.mockito.Mockito.mock(SnapshotReader.class);
        Set<String> fileNames = new java.util.HashSet<>(Collections.singletonList("f1"));
        when(mockReader.listFiles()).thenReturn(fileNames);
        when(mockReader.getPath()).thenReturn("/tmp");
        LocalFileMetaOutter.LocalFileMeta jraftMeta =
            LocalFileMetaOutter.LocalFileMeta.getDefaultInstance();
        when(mockReader.getFileMeta("f1")).thenReturn(jraftMeta);
        
        assertTrue(sm.onSnapshotLoad(mockReader));
    }
    
    /**
     * onSnapshotLoad with one file and non-empty userMeta -> JacksonUtils.toObj path.
     */
    @Test
    void testOnSnapshotLoadWithFileNonEmptyMeta() throws Exception {
        LocalFileMeta meta = new LocalFileMeta().append("k", "v");
        byte[] metaBytes = com.alibaba.nacos.common.utils.JacksonUtils.toJson(meta)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        LocalFileMetaOutter.LocalFileMeta jraftMeta = LocalFileMetaOutter.LocalFileMeta.newBuilder()
            .setUserMeta(ByteString.copyFrom(metaBytes)).build();
        SnapshotOperation loadOp = new SnapshotOperation() {
            
            @Override
            public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
            }
            
            @Override
            public boolean onSnapshotLoad(Reader reader) {
                LocalFileMeta loaded = reader.getFileMeta("f1");
                return loaded != null && "v".equals(loaded.get("k"));
            }
        };
        when(processor.loadSnapshotOperate()).thenReturn(Collections.singletonList(loadOp));
        NacosStateMachine sm = new NacosStateMachine(server, processor);
        SnapshotReader mockReader = org.mockito.Mockito.mock(SnapshotReader.class);
        Set<String> fileNames = new java.util.HashSet<>(Collections.singletonList("f1"));
        when(mockReader.listFiles()).thenReturn(fileNames);
        when(mockReader.getPath()).thenReturn("/tmp");
        when(mockReader.getFileMeta("f1")).thenReturn(jraftMeta);
        
        assertTrue(sm.onSnapshotLoad(mockReader));
    }
}
