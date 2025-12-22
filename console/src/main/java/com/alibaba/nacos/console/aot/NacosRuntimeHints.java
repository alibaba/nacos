/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.aot;

import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Add nacos runtime hints support.
 *
 * @author Dioxide.CN
 * @date 2024/8/6
 * @since 2.4.0
 */
@SuppressWarnings("all")
public class NacosRuntimeHints implements RuntimeHintsRegistrar {
    
    // region Java
    private final Class<?>[] javaClasses = {byte.class, byte.class, byte[].class, boolean.class, Object.class,
            Integer.class, com.sun.management.GarbageCollectorMXBean.class, com.sun.management.GcInfo.class,
            sun.misc.Unsafe.class, java.io.PrintWriter.class, java.lang.Double.class,
            java.lang.management.MemoryUsage.class, java.lang.management.BufferPoolMXBean.class,
            java.lang.management.ClassLoadingMXBean.class, java.lang.management.CompilationMXBean.class,
            java.lang.management.MemoryMXBean.class, java.lang.management.MemoryManagerMXBean.class,
            java.lang.management.MemoryPoolMXBean.class, java.lang.management.MonitorInfo.class,
            java.lang.management.ManagementPermission.class, java.lang.management.ThreadMXBean.class,
            java.lang.management.ThreadInfo.class, java.lang.management.LockInfo.class, java.lang.System.class,
            java.lang.Thread.class, java.net.InetSocketAddress.class, java.nio.ByteBuffer.class,
            java.nio.channels.SelectableChannel.class, java.nio.channels.SocketChannel.class,
            java.nio.channels.FileChannel.class, java.security.AccessController.class, java.sql.Date.class,
            java.sql.Driver.class, java.sql.DriverManager.class, java.sql.Time.class, java.sql.Timestamp.class,
            java.util.concurrent.locks.LockSupport.class, java.util.Optional.class, java.util.Properties.class};
    // endregion
    
    // region Hessian
    private final Class<?>[] hessianClasses = {com.caucho.hessian.io.Hessian2Input.class,
            com.caucho.hessian.io.ContextSerializerFactory.class};
    // endregion
    
    // region SQL
    private final Class<?>[] sqlClasses = {org.apache.derby.impl.store.raw.data.CachedPage.class,
            org.apache.derby.catalog.types.TypesImplInstanceGetter.class,
            org.apache.derby.impl.services.uuid.BasicUUIDGetter.class, org.apache.derby.iapi.types.DTSClassInfo.class,
            org.apache.derby.iapi.services.loader.ClassInfo.class, org.apache.derby.impl.io.DirStorageFactory.class,
            org.apache.derby.impl.store.raw.log.LogRecord.class, org.apache.derby.impl.store.raw.xact.XactId.class,
            org.apache.derby.impl.store.raw.log.CheckpointOperation.class,
            org.apache.derby.impl.store.raw.xact.TransactionTable.class,
            org.apache.derby.impl.store.raw.xact.TransactionTableEntry.class,
            org.apache.derby.impl.store.raw.log.LogCounter.class,
            org.apache.derby.impl.store.raw.log.ChecksumOperation.class,
            org.apache.derby.impl.store.raw.data.BaseDataFileFactoryJ4.class,
            org.apache.derby.impl.store.raw.xact.XactFactory.class, org.apache.derby.impl.store.raw.log.ReadOnly.class,
            org.apache.derby.impl.store.raw.xact.BeginXact.class, org.apache.derby.impl.store.raw.xact.EndXact.class,
            org.apache.derby.impl.store.raw.data.ContainerOperation.class,
            org.apache.derby.impl.store.raw.data.InitPageOperation.class,
            org.apache.derby.impl.store.raw.data.AllocPageOperation.class,
            org.apache.derby.impl.store.raw.data.InsertOperation.class,
            org.apache.derby.impl.store.raw.data.LogicalUndoOperation.class,
            org.apache.derby.impl.store.raw.data.InvalidatePageOperation.class,
            org.apache.derby.impl.store.raw.data.EncryptContainerOperation.class,
            org.apache.derby.impl.store.raw.data.EncryptContainerUndoOperation.class,
            org.apache.derby.impl.store.raw.data.CopyRowsOperation.class,
            org.apache.derby.impl.store.raw.data.ContainerUndoOperation.class,
            org.apache.derby.impl.store.raw.data.CompressSpacePageOperation.class,
            org.apache.derby.impl.store.raw.data.CompressSpacePageOperation10_2.class,
            org.apache.derby.impl.store.raw.data.ChainAllocPageOperation.class,
            org.apache.derby.jdbc.AutoloadedDriver.class, org.apache.derby.jdbc.InternalDriver.class,
            org.apache.derby.jdbc.Driver42.class, org.apache.derby.jdbc.EmbeddedDriver.class,
            org.apache.derby.jdbc.ResourceAdapterImpl.class, org.apache.derby.iapi.jdbc.DRDAServerStarter.class,
            org.apache.derby.iapi.jdbc.JDBCBoot.class, org.apache.derby.iapi.security.SecurityUtil.class,
            org.apache.derby.iapi.services.monitor.Monitor.class,
            org.apache.derby.iapi.services.stream.InfoStreams.class,
            org.apache.derby.impl.services.monitor.FileMonitor.class,
            org.apache.derby.impl.services.jmx.JMXManagementService.class,
            org.apache.derby.impl.services.cache.ConcurrentCacheFactory.class,
            org.apache.derby.impl.services.locks.ConcurrentPool.class,
            org.apache.derby.impl.services.jce.JCECipherFactoryBuilder.class,
            org.apache.derby.iapi.types.DataValueFactoryImpl.class,
            org.apache.derby.impl.store.raw.data.BaseDataFileFactory.class,
            org.apache.derby.impl.store.replication.master.MasterController.class,
            org.apache.derby.impl.sql.execute.RealResultSetStatisticsFactory.class,
            org.apache.derby.impl.jdbc.authentication.NoneAuthenticationServiceImpl.class,
            org.apache.derby.iapi.services.property.PropertyValidation.class,
            org.apache.derby.impl.sql.conn.GenericLanguageConnectionFactory.class,
            org.apache.derby.impl.sql.compile.OptimizerFactoryImpl.class,
            org.apache.derby.impl.services.bytecode.BCJava.class,
            org.apache.derby.impl.sql.execute.xplain.XPLAINFactory.class,
            org.apache.derby.impl.sql.compile.TypeCompilerFactoryImpl.class,
            org.apache.derby.impl.sql.catalog.DataDictionaryImpl.class,
            org.apache.derby.impl.sql.execute.GenericExecutionFactory.class,
            org.apache.derby.impl.services.daemon.SingleThreadDaemonFactory.class,
            org.apache.derby.impl.services.timer.SingletonTimerFactory.class,
            org.apache.derby.impl.jdbc.authentication.BasicAuthenticationServiceImpl.class,
            org.apache.derby.impl.db.BasicDatabase.class, org.apache.derby.impl.services.stream.SingleStream.class,
            org.apache.derby.impl.jdbc.authentication.NativeAuthenticationServiceImpl.class,
            org.apache.derby.impl.store.access.sort.ExternalSortFactory.class,
            org.apache.derby.impl.jdbc.authentication.SpecificAuthenticationServiceImpl.class,
            org.apache.derby.impl.store.access.sort.UniqueWithDuplicateNullsExternalSortFactory.class,
            org.apache.derby.impl.services.reflect.ReflectClassesJava2.class,
            org.apache.derby.impl.jdbc.authentication.JNDIAuthenticationService.class,
            org.apache.derby.impl.store.raw.log.LogToFile.class,
            org.apache.derby.impl.store.access.heap.HeapConglomerateFactory.class,
            org.apache.derby.impl.db.SlaveDatabase.class,
            org.apache.derby.impl.services.jmxnone.NoManagementService.class,
            org.apache.derby.impl.store.access.RllRAMAccessManager.class,
            org.apache.derby.impl.store.replication.slave.SlaveController.class,
            org.apache.derby.impl.sql.GenericLanguageFactory.class,
            org.apache.derby.impl.services.uuid.BasicUUIDFactory.class, org.apache.derby.impl.store.raw.RawStore.class,
            org.apache.derby.impl.store.access.btree.index.B2IFactory.class, com.zaxxer.hikari.HikariConfig.class,
            
            // hard code
            Class.forName("org.apache.derby.impl.services.monitor.ModuleInstance"),
            Class.forName("org.apache.derby.impl.services.monitor.ProtocolKey"),
            Class.forName("org.apache.derby.impl.services.monitor.TopService"),
            Class.forName("org.apache.derby.iapi.services.cache.ClassSizeCatalogImpl"),
            
            com.alibaba.nacos.persistence.datasource.ExternalDataSourceProperties.class};
    // endregion
    
    // region JRaft Entity
    private final Class<?>[] jraftDataClasses = {com.alipay.sofa.jraft.entity.LocalFileMetaOutter.LocalFileMeta.class,
            com.alipay.sofa.jraft.entity.LocalFileMetaOutter.LocalFileMeta.Builder.class,
            com.alipay.sofa.jraft.entity.LocalStorageOutter.LogPBMeta.class,
            com.alipay.sofa.jraft.entity.LocalStorageOutter.LogPBMeta.Builder.class,
            com.alipay.sofa.jraft.entity.LocalStorageOutter.StablePBMeta.class,
            com.alipay.sofa.jraft.entity.LocalStorageOutter.StablePBMeta.Builder.class,
            com.alipay.sofa.jraft.entity.LocalStorageOutter.ConfigurationPBMeta.class,
            com.alipay.sofa.jraft.entity.LocalStorageOutter.ConfigurationPBMeta.Builder.class,
            com.alipay.sofa.jraft.entity.LocalStorageOutter.LocalSnapshotPbMeta.class,
            com.alipay.sofa.jraft.entity.LocalStorageOutter.LocalSnapshotPbMeta.Builder.class,
            com.alipay.sofa.jraft.entity.LocalStorageOutter.LocalSnapshotPbMeta.File.class,
            com.alipay.sofa.jraft.entity.LocalStorageOutter.LocalSnapshotPbMeta.File.Builder.class,
            com.alipay.sofa.jraft.entity.RaftOutter.EntryMeta.class,
            com.alipay.sofa.jraft.entity.RaftOutter.EntryMeta.Builder.class,
            com.alipay.sofa.jraft.entity.RaftOutter.SnapshotMeta.class,
            com.alipay.sofa.jraft.entity.RaftOutter.SnapshotMeta.Builder.class,
            com.alipay.sofa.jraft.entity.codec.v2.LogOutter.PBLogEntry.class,
            com.alipay.sofa.jraft.entity.codec.v2.LogOutter.PBLogEntry.Builder.class,};
    // endregion
    
    // region JRaft RPC
    private final Class<?>[] jraftRpcClasses = {com.alipay.sofa.jraft.rpc.RpcRequests.AppendEntriesRequest.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.AppendEntriesRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.AppendEntriesResponse.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.AppendEntriesResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.AppendEntriesRequestHeader.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.AppendEntriesRequestHeader.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.PingRequest.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.PingRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.ErrorResponse.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.ErrorResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.InstallSnapshotRequest.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.InstallSnapshotRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.InstallSnapshotResponse.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.InstallSnapshotResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.TimeoutNowRequest.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.TimeoutNowRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.TimeoutNowResponse.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.TimeoutNowResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.RequestVoteRequest.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.RequestVoteRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.RequestVoteResponse.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.RequestVoteResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.GetFileRequest.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.GetFileRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.GetFileResponse.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.GetFileResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.ReadIndexRequest.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.ReadIndexRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.ReadIndexResponse.class,
            com.alipay.sofa.jraft.rpc.RpcRequests.ReadIndexResponse.Builder.class};
    // endregion
    
    // region JRaft CLI
    private final Class<?>[] jraftCliClasses = {com.alipay.sofa.jraft.rpc.CliRequests.LearnersOpResponse.class,
            com.alipay.sofa.jraft.rpc.CliRequests.LearnersOpResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.ResetLearnersRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.ResetLearnersRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.RemoveLearnersRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.RemoveLearnersRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.AddLearnersRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.AddLearnersRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.GetPeersResponse.class,
            com.alipay.sofa.jraft.rpc.CliRequests.GetPeersResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.GetPeersRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.GetPeersRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.GetLeaderResponse.class,
            com.alipay.sofa.jraft.rpc.CliRequests.GetLeaderResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.GetLeaderRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.GetLeaderRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.TransferLeaderRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.TransferLeaderRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.ResetPeerRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.ResetPeerRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.SnapshotRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.SnapshotRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.ChangePeersResponse.class,
            com.alipay.sofa.jraft.rpc.CliRequests.ChangePeersResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.ChangePeersRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.ChangePeersRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.RemovePeerResponse.class,
            com.alipay.sofa.jraft.rpc.CliRequests.RemovePeerResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.RemovePeerRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.RemovePeerRequest.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.AddPeerResponse.class,
            com.alipay.sofa.jraft.rpc.CliRequests.AddPeerResponse.Builder.class,
            com.alipay.sofa.jraft.rpc.CliRequests.AddPeerRequest.class,
            com.alipay.sofa.jraft.rpc.CliRequests.AddPeerRequest.Builder.class};
    // endregion
    
    // region JRaft Service
    private final Class<?>[] jraftUtilClasses = {com.alipay.sofa.jraft.rpc.ProtobufMsgFactory.class,
            com.alipay.sofa.jraft.rpc.RpcRequestClosure.class,
            com.alipay.sofa.jraft.rpc.impl.AbstractClientService.class,
            com.alipay.sofa.jraft.rpc.impl.BoltRaftRpcFactory.class,
            com.alipay.sofa.jraft.rpc.impl.GrpcRaftRpcFactory.class,
            com.alipay.sofa.jraft.util.JRaftSignalHandler.class,
            com.alipay.sofa.jraft.util.concurrent.MpscSingleThreadExecutor.class,
            com.alipay.sofa.jraft.util.timer.DefaultRaftTimerFactory.class,
            com.alipay.sofa.jraft.util.internal.ThrowUtil.class,
            com.alipay.sofa.jraft.core.DefaultJRaftServiceFactory.class, com.alipay.sofa.jraft.core.NodeImpl.class,
            com.alipay.sofa.jraft.core.Replicator.class,
            com.alipay.sofa.jraft.storage.snapshot.local.LocalSnapshotReader.class};
    // endregion
    
    // region gRpc
    private final Class<?>[] grpcClasses = {com.google.protobuf.Any.class, com.google.protobuf.Any.Builder.class,
            com.google.protobuf.ByteString.class, com.google.protobuf.Message.class,
            com.google.protobuf.ByteString.class, com.google.protobuf.CodedInputStream.class,
            com.google.common.util.concurrent.AbstractFuture.class,
            com.google.common.util.concurrent.ListenableFuture.class,
            
            io.grpc.KnownLength.class, io.grpc.ServerCall.class, io.grpc.ServerBuilder.class,
            io.grpc.stub.ClientCalls.class, io.grpc.stub.ServerCalls.class, io.grpc.stub.ServerCallStreamObserver.class,
            io.grpc.stub.CallStreamObserver.class, io.grpc.stub.StreamObserver.class,
            io.grpc.internal.ReadableBuffers.class, io.grpc.internal.ServerImplBuilder.class,
            io.grpc.internal.ClientStreamListener.class, io.grpc.netty.shaded.io.netty.bootstrap.ServerBootstrap.class,
            io.grpc.netty.shaded.io.netty.buffer.AbstractByteBufAllocator.class,
            io.grpc.netty.shaded.io.netty.buffer.AbstractReferenceCountedByteBuf.class,
            io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator.class,
            io.grpc.netty.shaded.io.netty.buffer.ByteBufUtil.class,
            io.grpc.netty.shaded.io.netty.buffer.PooledByteBufAllocator.class,
            io.grpc.netty.shaded.io.netty.buffer.UnpooledDirectByteBuf.class,
            io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder.class,
            io.grpc.netty.shaded.io.grpc.netty.NettyServerProvider.class,
            io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder.class,
            io.grpc.netty.shaded.io.grpc.netty.NettyChannelProvider.class,
            io.grpc.netty.shaded.io.grpc.netty.NettyConnectionHelper.class,
            io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel.class,
            io.grpc.netty.shaded.io.netty.util.AttributeKey.class, io.grpc.ForwardingServerCall.class,
            io.grpc.ForwardingServerCall.SimpleForwardingServerCall.class,
            
            Class.forName("io.grpc.internal.ServerCallImpl"),
            Class.forName("io.grpc.netty.shaded.io.grpc.netty.WriteQueue"),
            Class.forName("io.grpc.netty.shaded.io.grpc.netty.NettyServerStream")};
    // endregion
    
    // region Nacos Hints
    private final Class<?>[] nacosClasses = {
            // reflect
            com.alibaba.nacos.common.notify.SlowEvent.class, com.alibaba.nacos.common.packagescan.PackageScan.class,
            com.alibaba.nacos.common.packagescan.DefaultPackageScan.class,
            com.alibaba.nacos.naming.controllers.CatalogController.class,
            com.alibaba.nacos.naming.core.v2.event.metadata.MetadataEvent.ServiceMetadataEvent.class,
            com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl.class,
            com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl.InstanceStoreRequest.class,
            com.alibaba.nacos.persistence.datasource.LocalDataSourceServiceImpl.class,
            com.alibaba.nacos.persistence.configuration.condition.ConditionStandaloneEmbedStorage.class,
            com.alibaba.nacos.consistency.snapshot.LocalFileMeta.class,
            com.alibaba.nacos.consistency.ProtocolMetaData.class,
            com.alibaba.nacos.common.remote.client.grpc.GrpcUtils.class,
            com.alibaba.nacos.api.naming.remote.request.AbstractNamingRequest.class,
            com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest.class,
            com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest.ConfigListenContext.class,
            com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse.class,
            com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse.ConfigContext.class,
            com.alibaba.nacos.api.remote.response.ClientDetectionResponse.class,
            com.alibaba.nacos.api.remote.response.ConnectResetResponse.class,
            com.alibaba.nacos.api.remote.response.ErrorResponse.class,
            com.alibaba.nacos.api.remote.response.HealthCheckResponse.class,
            com.alibaba.nacos.api.remote.response.Response.class,
            com.alibaba.nacos.api.remote.response.ServerCheckResponse.class,
            com.alibaba.nacos.api.remote.response.ServerLoaderInfoResponse.class,
            com.alibaba.nacos.api.remote.response.ServerReloadResponse.class,
            com.alibaba.nacos.api.remote.response.SetupAckResponse.class,
            // cluster
            com.alibaba.nacos.consistency.ap.APProtocol.class, com.alibaba.nacos.consistency.cp.CPProtocol.class,
            com.alibaba.nacos.core.distributed.raft.RaftConfig.class,
            com.alibaba.nacos.core.distributed.raft.RaftEvent.class,
            com.alibaba.nacos.api.ability.ServerAbilities.class,
            com.alibaba.nacos.api.config.ability.ServerConfigAbility.class,
            com.alibaba.nacos.api.naming.ability.ServerNamingAbility.class,
            com.alibaba.nacos.consistency.DataOperation.class, com.alibaba.nacos.core.cluster.Member.class,
            com.alibaba.nacos.core.cluster.remote.request.AbstractClusterRequest.class,
            com.alibaba.nacos.core.cluster.remote.request.MemberReportRequest.class,
            com.alibaba.nacos.core.cluster.remote.response.MemberReportResponse.class,
            com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy.class,
            com.alibaba.nacos.core.distributed.distro.entity.DistroData.class,
            com.alibaba.nacos.core.distributed.distro.entity.DistroKey.class,
            com.alibaba.nacos.naming.cluster.remote.request.DistroDataRequest.class,
            com.alibaba.nacos.naming.cluster.remote.response.DistroDataResponse.class,
            com.alibaba.nacos.naming.core.v2.client.ClientSyncDatumSnapshot.class,
            com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo.class,
            // proto
            com.alibaba.nacos.consistency.entity.WriteRequest.class,
            com.alibaba.nacos.consistency.entity.WriteRequest.Builder.class,
            com.alibaba.nacos.consistency.entity.ReadRequest.class,
            com.alibaba.nacos.consistency.entity.ReadRequest.Builder.class,
            com.alibaba.nacos.consistency.entity.Response.class,
            com.alibaba.nacos.consistency.entity.Response.Builder.class,
            com.alibaba.nacos.consistency.entity.GetRequest.class,
            com.alibaba.nacos.consistency.entity.GetRequest.Builder.class,
            com.alibaba.nacos.consistency.entity.Log.class, com.alibaba.nacos.consistency.entity.Log.Builder.class,
            // grpc
            com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc.class,
            com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc.BiRequestStreamBlockingStub.class,
            com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc.BiRequestStreamFutureStub.class,
            com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc.BiRequestStreamImplBase.class,
            com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc.BiRequestStreamStub.class,
            com.alibaba.nacos.api.grpc.auto.Metadata.class, com.alibaba.nacos.api.grpc.auto.Metadata.Builder.class,
            com.alibaba.nacos.api.grpc.auto.NacosGrpcService.class, com.alibaba.nacos.api.grpc.auto.Payload.class,
            com.alibaba.nacos.api.grpc.auto.Payload.Builder.class, com.alibaba.nacos.api.grpc.auto.RequestGrpc.class,
            com.alibaba.nacos.api.grpc.auto.RequestGrpc.RequestStub.class,
            com.alibaba.nacos.api.grpc.auto.RequestGrpc.RequestBlockingStub.class,
            com.alibaba.nacos.api.grpc.auto.RequestGrpc.RequestFutureStub.class,
            com.alibaba.nacos.api.grpc.auto.RequestGrpc.RequestImplBase.class,
            com.alibaba.nacos.api.naming.remote.request.InstanceRequest.class,
            com.alibaba.nacos.api.naming.remote.request.PersistentInstanceRequest.class,
            com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest.class,
            com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest.class,
            com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest.class,
            com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest.class,
            com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest.class,
            com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest.class,
            com.alibaba.nacos.api.config.remote.request.cluster.ConfigChangeClusterSyncRequest.class,
            com.alibaba.nacos.api.config.remote.response.ClientConfigMetricResponse.class,
            com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse.class,
            com.alibaba.nacos.api.config.remote.response.ConfigChangeNotifyResponse.class,
            com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse.class,
            com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse.class,
            com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse.class,
            com.alibaba.nacos.api.config.remote.response.cluster.ConfigChangeClusterSyncResponse.class,
            com.alibaba.nacos.api.naming.remote.request.BatchInstanceRequest.class,
            com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest.class,
            com.alibaba.nacos.api.naming.remote.request.ServiceListRequest.class,
            com.alibaba.nacos.api.naming.remote.request.ServiceQueryRequest.class,
            com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest.class,
            com.alibaba.nacos.api.naming.remote.response.BatchInstanceResponse.class,
            com.alibaba.nacos.api.naming.remote.response.InstanceResponse.class,
            com.alibaba.nacos.api.naming.remote.response.NotifySubscriberResponse.class,
            com.alibaba.nacos.api.naming.remote.response.QueryServiceResponse.class,
            com.alibaba.nacos.api.naming.remote.response.ServiceListResponse.class,
            com.alibaba.nacos.api.naming.remote.response.SubscribeServiceResponse.class,
            com.alibaba.nacos.api.remote.request.ClientDetectionRequest.class,
            com.alibaba.nacos.api.remote.request.ConnectionSetupRequest.class,
            com.alibaba.nacos.api.remote.request.ConnectResetRequest.class,
            com.alibaba.nacos.api.remote.request.HealthCheckRequest.class,
            com.alibaba.nacos.api.remote.request.InternalRequest.class,
            com.alibaba.nacos.api.remote.request.PushAckRequest.class,
            com.alibaba.nacos.api.remote.request.Request.class, com.alibaba.nacos.api.remote.request.RequestMeta.class,
            com.alibaba.nacos.api.remote.request.ServerCheckRequest.class,
            com.alibaba.nacos.api.remote.request.ServerLoaderInfoRequest.class,
            com.alibaba.nacos.api.remote.request.ServerReloadRequest.class,
            com.alibaba.nacos.api.remote.request.SetupAckRequest.class,
            com.alibaba.nacos.api.remote.response.ClientDetectionResponse.class,
            com.alibaba.nacos.api.remote.response.ConnectResetResponse.class,
            com.alibaba.nacos.api.remote.response.ErrorResponse.class,
            com.alibaba.nacos.api.remote.response.HealthCheckResponse.class,
            com.alibaba.nacos.api.remote.response.ServerCheckResponse.class,
            com.alibaba.nacos.api.remote.response.ServerLoaderInfoResponse.class,
            com.alibaba.nacos.api.remote.response.ServerReloadResponse.class,
            com.alibaba.nacos.api.remote.response.SetupAckResponse.class,
            com.alibaba.nacos.core.distributed.raft.NacosClosure.class,
            com.alibaba.nacos.core.monitor.GrpcServerThreadPoolMonitor.class,
            com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.class, com.alibaba.nacos.core.remote.BaseRpcServer.class,
            com.alibaba.nacos.core.remote.grpc.GrpcBiStreamRequestAcceptor.class,
            com.alibaba.nacos.core.remote.grpc.GrpcClusterServer.class,
            com.alibaba.nacos.core.remote.grpc.GrpcSdkServer.class,
            com.alibaba.nacos.core.cluster.remote.request.MemberReportRequest.class,
            com.alibaba.nacos.core.cluster.remote.response.MemberReportResponse.class,
            com.alibaba.nacos.naming.cluster.remote.request.DistroDataRequest.class,
            com.alibaba.nacos.naming.cluster.remote.response.DistroDataResponse.class,
            // serializer
            com.alibaba.nacos.consistency.serialize.HessianSerializer.class,
            com.alibaba.nacos.consistency.serialize.JacksonSerializer.class,
            com.alibaba.nacos.consistency.serialize.NacosHessianSerializerFactory.class,
            com.alibaba.nacos.naming.core.v2.client.ClientSyncData.class,
            com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata.class,
            com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo.class,
            com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo.class,
            com.alibaba.nacos.naming.core.v2.pojo.BatchInstanceData.class,
            com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo.class,
            com.alibaba.nacos.naming.core.v2.pojo.Service.class, com.alibaba.nacos.api.naming.pojo.Cluster.class,
            com.alibaba.nacos.api.naming.pojo.Instance.class, com.alibaba.nacos.api.naming.pojo.ListView.class,
            com.alibaba.nacos.api.naming.pojo.Service.class, com.alibaba.nacos.api.naming.pojo.ServiceInfo.class,
            com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder.class,
            com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker.class,
            com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckerFactory.class,
            com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http.class,
            com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql.class,
            com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp.class,
            com.alibaba.nacos.api.remote.request.Request.class, ServiceView.class,
            com.alibaba.nacos.naming.pojo.Subscriber.class, com.alibaba.nacos.naming.pojo.Subscribers.class,
            com.alibaba.nacos.naming.pojo.ClusterInfo.class, com.alibaba.nacos.naming.pojo.InstanceOperationInfo.class,
            com.alibaba.nacos.naming.pojo.IpAddressInfo.class, com.alibaba.nacos.naming.pojo.Record.class,
            com.alibaba.nacos.naming.pojo.ServiceDetailInfo.class, com.alibaba.nacos.naming.pojo.ServiceNameView.class,
            // sys and plugin
            com.alibaba.nacos.config.server.filter.ConfigEnabledFilter.class,
            com.alibaba.nacos.naming.config.NamingEnabledFilter.class,
            com.alibaba.nacos.istio.config.IstioEnabledFilter.class, com.alibaba.nacos.config.server.Config.class,
            com.alibaba.nacos.naming.NamingApp.class, com.alibaba.nacos.cmdb.CmdbApp.class,
            com.alibaba.nacos.istio.IstioApp.class, com.alibaba.nacos.prometheus.PrometheusApp.class,
            com.alibaba.nacos.sys.filter.NacosTypeExcludeFilter.class,
            com.alibaba.nacos.sys.filter.NacosPackageExcludeFilter.class,
            com.alibaba.nacos.plugin.control.ControlManagerCenter.class,
            com.alibaba.nacos.plugin.control.connection.ConnectionMetricsCollector.class,
            com.alibaba.nacos.config.server.service.LongPollingConnectionMetricsCollector.class,
            com.alibaba.nacos.core.remote.RuntimeConnectionEjector.class,
            com.alibaba.nacos.core.remote.ConnectionManager.class,
            com.alibaba.nacos.config.server.remote.RpcConfigChangeNotifier.class,};
    // endregion
    
    // region Nacos Serializer
    private final List<Class<? extends Serializable>> serializer = List.of(byte.class, byte[].class, String.class,
            ConcurrentHashMap.class, com.alibaba.nacos.api.grpc.auto.Metadata.class,
            com.alibaba.nacos.api.grpc.auto.Payload.class, com.alibaba.nacos.naming.core.v2.client.ClientSyncData.class,
            com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata.class,
            com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo.class,
            com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo.class,
            com.alibaba.nacos.naming.core.v2.pojo.BatchInstanceData.class,
            com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo.class,
            com.alibaba.nacos.naming.core.v2.pojo.Service.class,
            com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl.InstanceStoreRequest.class,
            com.alibaba.nacos.api.naming.pojo.Cluster.class, com.alibaba.nacos.api.naming.pojo.Instance.class,
            com.alibaba.nacos.api.naming.pojo.Service.class,
            com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http.class,
            com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql.class,
            com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp.class,
            com.alibaba.nacos.naming.pojo.Subscriber.class, com.alibaba.nacos.naming.pojo.Subscribers.class,
            com.alibaba.nacos.naming.pojo.ClusterInfo.class, com.alibaba.nacos.naming.pojo.IpAddressInfo.class,
            com.alibaba.nacos.naming.pojo.Record.class, com.alibaba.nacos.naming.pojo.ServiceDetailInfo.class,
            com.alibaba.nacos.consistency.entity.WriteRequest.class,
            com.alibaba.nacos.consistency.entity.ReadRequest.class, com.alibaba.nacos.consistency.entity.Response.class,
            com.alibaba.nacos.consistency.entity.GetRequest.class, com.alibaba.nacos.consistency.entity.Log.class);
    // endregion
    
    private final String[] resourcePattern = {AotConfiguration.reflectToNativeLibraryLoader(),
            ".*libnetty_transport_native_epoll_.*\\.so", ".*\\.desc$", ".*\\.html$", ".*\\.css$", ".*\\.js$",
            ".*\\.js.map$", ".*\\.png$", ".*\\.svg$", ".*\\.eot$", ".*\\.woff$", ".*\\.woff2$", ".*\\.ttf$",
            "org/apache/derby/modules.properties", "application.properties",};
    
    public NacosRuntimeHints() throws ClassNotFoundException {
    }
    
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        Stream.of(javaClasses, hessianClasses, sqlClasses, grpcClasses, jraftDataClasses, jraftRpcClasses,
                jraftCliClasses, jraftUtilClasses, nacosClasses).flatMap(Stream::of).forEach(type -> hints.reflection()
                .registerType(type, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.DECLARED_FIELDS, MemberCategory.DECLARED_CLASSES));

        // Register optional plugin classes by name to avoid compile-time dependency
        registerOptionalClass(hints, "com.alibaba.nacos.plugin.auth.impl.jwt.NacosJwtPayload");

        for (String pattern : resourcePattern) {
            hints.resources().registerPattern(pattern);
        }

        serializer.forEach(type -> hints.serialization().registerType(type));
    }

    private void registerOptionalClass(RuntimeHints hints, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            hints.reflection().registerType(clazz, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS,
                    MemberCategory.DECLARED_CLASSES);
        } catch (ClassNotFoundException e) {
            // Optional plugin class not available, skip registration
        }
    }

}
