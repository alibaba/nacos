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

package com.alibaba.nacos.config.server.service.repository.embedded;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionDistributedEmbedStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.exception.NJdbcException;
import com.alibaba.nacos.config.server.model.event.ConfigDumpEvent;
import com.alibaba.nacos.config.server.model.event.DerbyLoadEvent;
import com.alibaba.nacos.config.server.model.event.RaftDbErrorEvent;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.config.server.service.dump.DumpConfigHandler;
import com.alibaba.nacos.config.server.service.repository.RowMapperManager;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import com.alibaba.nacos.config.server.service.sql.QueryType;
import com.alibaba.nacos.config.server.service.sql.SelectRequest;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.core.utils.GenericType;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Distributed Database Operate.
 *
 * <pre>
 *                   ┌────────────────────┐
 *               ┌──▶│   PersistService   │
 *               │   └────────────────────┘ ┌─────────────────┐
 *               │              │           │                 │
 *               │              │           │                 ▼
 *               │              │           │    ┌────────────────────────┐
 *               │              │           │    │  acquireSnakeflowerId  │
 *               │              │           │    └────────────────────────┘
 *               │              │           │                 │
 *               │              │           │                 │
 *               │              │           │                 ▼
 *               │              │           │      ┌────────────────────┐          save sql
 *               │              ▼           │      │     saveConfig     │──────────context─────────────┐
 *               │     ┌────────────────┐   │      └────────────────────┘                              │
 *               │     │ publishConfig  │───┘                 │                                        │
 *               │     └────────────────┘                     │                                        │
 *               │                                            ▼                                        ▼
 *               │                               ┌─────────────────────────┐    save sql    ┌────────────────────┐
 *               │                               │ saveConfigTagRelations  │────context────▶│  SqlContextUtils   │◀─┐
 *        publish config                         └─────────────────────────┘                └────────────────────┘  │
 *               │                                            │                                        ▲            │
 *               │                                            │                                        │            │
 *               │                                            ▼                                        │            │
 *               │                                ┌───────────────────────┐         save sql           │            │
 *            ┌────┐                              │   saveConfigHistory   │─────────context────────────┘            │
 *            │user│                              └───────────────────────┘                                         │
 *            └────┘                                                                                                │
 *               ▲                                                                                                  │
 *               │                                           ┌1:getCurrentSqlContexts───────────────────────────────┘
 *               │                                           │
 *               │                                           │
 *               │                                           │
 *               │           ┌───────────────┐    ┌─────────────────────┐
 *               │           │ JdbcTemplate  │◀───│   DatabaseOperate   │───┐
 *       4:execute result    └───────────────┘    └─────────────────────┘   │
 *               │                   │                       ▲              │
 *               │                   │                       │              │
 *               │                   │                  3:onApply         2:submit(List&lt;ModifyRequest&gt;)
 *               │                   │                       │              │
 *               │                   ▼                       │              │
 *               │           ┌──────────────┐                │              │
 *               │           │ Apache Derby │    ┌───────────────────────┐  │
 *               │           └──────────────┘    │     JRaftProtocol     │◀─┘
 *               │                               └───────────────────────┘
 *               │                                           │
 *               │                                           │
 *               └───────────────────────────────────────────┘
 * </pre>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(ConditionDistributedEmbedStorage.class)
@Component
@SuppressWarnings({"unchecked"})
public class DistributedDatabaseOperateImpl extends RequestProcessor4CP implements BaseDatabaseOperate {
    
    /**
     * The data import operation is dedicated key, which ACTS as an identifier.
     */
    private static final String DATA_IMPORT_KEY = "00--0-data_import-0--00";
    
    private ServerMemberManager memberManager;
    
    private CPProtocol protocol;
    
    private LocalDataSourceServiceImpl dataSourceService;
    
    private JdbcTemplate jdbcTemplate;
    
    private TransactionTemplate transactionTemplate;
    
    private Serializer serializer = SerializeFactory.getDefault();
    
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    
    private ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
    
    public DistributedDatabaseOperateImpl(ServerMemberManager memberManager, ProtocolManager protocolManager)
            throws Exception {
        this.memberManager = memberManager;
        this.protocol = protocolManager.getCpProtocol();
        init();
    }
    
    protected void init() throws Exception {
        
        this.dataSourceService = (LocalDataSourceServiceImpl) DynamicDataSource.getInstance().getDataSource();
        
        // Because in Raft + Derby mode, ensuring data consistency depends on the Raft's
        // log playback and snapshot recovery capabilities, and the last data must be cleared
        this.dataSourceService.cleanAndReopenDerby();
        
        this.jdbcTemplate = dataSourceService.getJdbcTemplate();
        this.transactionTemplate = dataSourceService.getTransactionTemplate();
        
        // Registers a Derby Raft state machine failure event for node degradation processing
        NotifyCenter.registerToSharePublisher(RaftDbErrorEvent.class);
        // Register the snapshot load event
        NotifyCenter.registerToSharePublisher(DerbyLoadEvent.class);
        
        NotifyCenter.registerSubscriber(new Subscriber<RaftDbErrorEvent>() {
            @Override
            public void onEvent(RaftDbErrorEvent event) {
                dataSourceService.setHealthStatus("DOWN");
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return RaftDbErrorEvent.class;
            }
        });
        
        NotifyCenter.registerToPublisher(ConfigDumpEvent.class, NotifyCenter.ringBufferSize);
        NotifyCenter.registerSubscriber(new DumpConfigHandler());
        
        this.protocol.addRequestProcessors(Collections.singletonList(this));
        LogUtil.DEFAULT_LOG.info("use DistributedTransactionServicesImpl");
    }
    
    @JustForTest
    public void mockConsistencyProtocol(CPProtocol protocol) {
        this.protocol = protocol;
    }
    
    @Override
    public <R> R queryOne(String sql, Class<R> cls) {
        try {
            LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "queryOne info : sql : {}", sql);
            
            byte[] data = serializer.serialize(
                    SelectRequest.builder().queryType(QueryType.QUERY_ONE_NO_MAPPER_NO_ARGS).sql(sql)
                            .className(cls.getCanonicalName()).build());
            
            final boolean blockRead = EmbeddedStorageContextUtils
                    .containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);
            
            Response response = innerRead(
                    ReadRequest.newBuilder().setGroup(group()).setData(ByteString.copyFrom(data)).build(), blockRead);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray(), cls);
            }
            throw new NJdbcException(response.getErrMsg(), response.getErrMsg());
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("An exception occurred during the query operation : {}", e.toString());
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
        }
    }
    
    @Override
    public <R> R queryOne(String sql, Object[] args, Class<R> cls) {
        try {
            LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "queryOne info : sql : {}, args : {}", sql, args);
            
            byte[] data = serializer.serialize(
                    SelectRequest.builder().queryType(QueryType.QUERY_ONE_NO_MAPPER_WITH_ARGS).sql(sql).args(args)
                            .className(cls.getCanonicalName()).build());
            
            final boolean blockRead = EmbeddedStorageContextUtils
                    .containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);
            
            Response response = innerRead(
                    ReadRequest.newBuilder().setGroup(group()).setData(ByteString.copyFrom(data)).build(), blockRead);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray(), cls);
            }
            throw new NJdbcException(response.getErrMsg(), response.getErrMsg());
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("An exception occurred during the query operation : {}", e.toString());
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
        }
    }
    
    @Override
    public <R> R queryOne(String sql, Object[] args, RowMapper<R> mapper) {
        try {
            LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "queryOne info : sql : {}, args : {}", sql, args);
            
            byte[] data = serializer.serialize(
                    SelectRequest.builder().queryType(QueryType.QUERY_ONE_WITH_MAPPER_WITH_ARGS).sql(sql).args(args)
                            .className(mapper.getClass().getCanonicalName()).build());
            
            final boolean blockRead = EmbeddedStorageContextUtils
                    .containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);
            
            Response response = innerRead(
                    ReadRequest.newBuilder().setGroup(group()).setData(ByteString.copyFrom(data)).build(), blockRead);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray(),
                        ClassUtils.resolveGenericTypeByInterface(mapper.getClass()));
            }
            throw new NJdbcException(response.getErrMsg(), response.getErrMsg());
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("An exception occurred during the query operation : {}", e.toString());
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
        }
    }
    
    @Override
    public <R> List<R> queryMany(String sql, Object[] args, RowMapper<R> mapper) {
        try {
            LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "queryMany info : sql : {}, args : {}", sql, args);
            
            byte[] data = serializer.serialize(
                    SelectRequest.builder().queryType(QueryType.QUERY_MANY_WITH_MAPPER_WITH_ARGS).sql(sql).args(args)
                            .className(mapper.getClass().getCanonicalName()).build());
            
            final boolean blockRead = EmbeddedStorageContextUtils
                    .containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);
            
            Response response = innerRead(
                    ReadRequest.newBuilder().setGroup(group()).setData(ByteString.copyFrom(data)).build(), blockRead);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray(), List.class);
            }
            throw new NJdbcException(response.getErrMsg());
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("An exception occurred during the query operation : {}", e.toString());
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
        }
    }
    
    @Override
    public <R> List<R> queryMany(String sql, Object[] args, Class<R> rClass) {
        try {
            LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "queryMany info : sql : {}, args : {}", sql, args);
            
            byte[] data = serializer.serialize(
                    SelectRequest.builder().queryType(QueryType.QUERY_MANY_NO_MAPPER_WITH_ARGS).sql(sql).args(args)
                            .className(rClass.getCanonicalName()).build());
            
            final boolean blockRead = EmbeddedStorageContextUtils
                    .containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);
            
            Response response = innerRead(
                    ReadRequest.newBuilder().setGroup(group()).setData(ByteString.copyFrom(data)).build(), blockRead);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray(), List.class);
            }
            throw new NJdbcException(response.getErrMsg());
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("An exception occurred during the query operation : {}", e.toString());
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
        }
    }
    
    @Override
    public List<Map<String, Object>> queryMany(String sql, Object[] args) {
        try {
            LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "queryMany info : sql : {}, args : {}", sql, args);
            
            byte[] data = serializer.serialize(
                    SelectRequest.builder().queryType(QueryType.QUERY_MANY_WITH_LIST_WITH_ARGS).sql(sql).args(args)
                            .build());
            
            final boolean blockRead = EmbeddedStorageContextUtils
                    .containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);
            
            Response response = innerRead(
                    ReadRequest.newBuilder().setGroup(group()).setData(ByteString.copyFrom(data)).build(), blockRead);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray(), List.class);
            }
            throw new NJdbcException(response.getErrMsg());
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("An exception occurred during the query operation : {}", e.toString());
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
        }
    }
    
    /**
     * In some business situations, you need to avoid the timeout issue, so blockRead is used to determine this.
     *
     * @param request   {@link ReadRequest}
     * @param blockRead is async read operation
     * @return {@link Response}
     * @throws Exception Exception
     */
    private Response innerRead(ReadRequest request, boolean blockRead) throws Exception {
        if (blockRead) {
            return (Response) protocol.aGetData(request).join();
        }
        return protocol.getData(request);
    }
    
    @Override
    public CompletableFuture<RestResult<String>> dataImport(File file) {
        return CompletableFuture.supplyAsync(() -> {
            try (DiskUtils.LineIterator iterator = DiskUtils.lineIterator(file)) {
                int batchSize = 1000;
                List<String> batchUpdate = new ArrayList<>(batchSize);
                List<CompletableFuture<Response>> futures = new ArrayList<>();
                while (iterator.hasNext()) {
                    String sql = iterator.next();
                    if (StringUtils.isNotBlank(sql)) {
                        batchUpdate.add(sql);
                    }
                    boolean submit = batchUpdate.size() == batchSize || !iterator.hasNext();
                    if (submit) {
                        List<ModifyRequest> requests = batchUpdate.stream().map(ModifyRequest::new)
                                .collect(Collectors.toList());
                        CompletableFuture<Response> future = protocol.writeAsync(WriteRequest.newBuilder().setGroup(group())
                                .setData(ByteString.copyFrom(serializer.serialize(requests)))
                                .putExtendInfo(DATA_IMPORT_KEY, Boolean.TRUE.toString()).build());
                        futures.add(future);
                        batchUpdate.clear();
                    }
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                for (CompletableFuture<Response> future : futures) {
                    Response response = future.get();
                    if (!response.getSuccess()) {
                        return RestResultUtils.failed(response.getErrMsg());
                    }
                }
                return RestResultUtils.success();
            } catch (Throwable ex) {
                LogUtil.DEFAULT_LOG.error("data import has error :", ex);
                return RestResultUtils.failed(ex.getMessage());
            }
        });
    }
    
    @Override
    public Boolean update(List<ModifyRequest> sqlContext, BiConsumer<Boolean, Throwable> consumer) {
        try {
            
            // Since the SQL parameter is Object[], in order to ensure that the types of
            // array elements are not lost, the serialization here is done using the java-specific
            // serialization framework, rather than continuing with the protobuff
            
            LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "modifyRequests info : {}", sqlContext);
            
            // {timestamp}-{group}-{ip:port}-{signature}
            
            final String key =
                    System.currentTimeMillis() + "-" + group() + "-" + memberManager.getSelf().getAddress() + "-"
                            + MD5Utils.md5Hex(sqlContext.toString(), Constants.ENCODE);
            WriteRequest request = WriteRequest.newBuilder().setGroup(group()).setKey(key)
                    .setData(ByteString.copyFrom(serializer.serialize(sqlContext)))
                    .putAllExtendInfo(EmbeddedStorageContextUtils.getCurrentExtendInfo())
                    .setType(sqlContext.getClass().getCanonicalName()).build();
            if (Objects.isNull(consumer)) {
                Response response = this.protocol.write(request);
                if (response.getSuccess()) {
                    return true;
                }
                LogUtil.DEFAULT_LOG.error("execute sql modify operation failed : {}", response.getErrMsg());
                return false;
            } else {
                this.protocol.writeAsync(request).whenComplete((BiConsumer<Response, Throwable>) (response, ex) -> {
                    String errMsg = Objects.isNull(ex) ? response.getErrMsg() : ExceptionUtil.getCause(ex).getMessage();
                    consumer.accept(response.getSuccess(),
                            StringUtils.isBlank(errMsg) ? null : new NJdbcException(errMsg));
                });
            }
            return true;
        } catch (TimeoutException e) {
            LogUtil.FATAL_LOG.error("An timeout exception occurred during the update operation");
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
        } catch (Throwable e) {
            LogUtil.FATAL_LOG.error("An exception occurred during the update operation : {}", e);
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
        }
    }
    
    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new DerbySnapshotOperation(writeLock));
    }
    
    @SuppressWarnings("all")
    @Override
    public Response onRequest(final ReadRequest request) {
        final SelectRequest selectRequest = serializer
                .deserialize(request.getData().toByteArray(), SelectRequest.class);
        
        LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "getData info : selectRequest : {}", selectRequest);
        
        final RowMapper<Object> mapper = RowMapperManager.getRowMapper(selectRequest.getClassName());
        final byte type = selectRequest.getQueryType();
        readLock.lock();
        Object data;
        try {
            switch (type) {
                case QueryType.QUERY_ONE_WITH_MAPPER_WITH_ARGS:
                    data = queryOne(jdbcTemplate, selectRequest.getSql(), selectRequest.getArgs(), mapper);
                    break;
                case QueryType.QUERY_ONE_NO_MAPPER_NO_ARGS:
                    data = queryOne(jdbcTemplate, selectRequest.getSql(),
                            ClassUtils.findClassByName(selectRequest.getClassName()));
                    break;
                case QueryType.QUERY_ONE_NO_MAPPER_WITH_ARGS:
                    data = queryOne(jdbcTemplate, selectRequest.getSql(), selectRequest.getArgs(),
                            ClassUtils.findClassByName(selectRequest.getClassName()));
                    break;
                case QueryType.QUERY_MANY_WITH_MAPPER_WITH_ARGS:
                    data = queryMany(jdbcTemplate, selectRequest.getSql(), selectRequest.getArgs(), mapper);
                    break;
                case QueryType.QUERY_MANY_WITH_LIST_WITH_ARGS:
                    data = queryMany(jdbcTemplate, selectRequest.getSql(), selectRequest.getArgs());
                    break;
                case QueryType.QUERY_MANY_NO_MAPPER_WITH_ARGS:
                    data = queryMany(jdbcTemplate, selectRequest.getSql(), selectRequest.getArgs(),
                            ClassUtils.findClassByName(selectRequest.getClassName()));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported data query categories");
            }
            ByteString bytes = data == null ? ByteString.EMPTY : ByteString.copyFrom(serializer.serialize(data));
            return Response.newBuilder().setSuccess(true).setData(bytes).build();
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("There was an error querying the data, request : {}, error : {}", selectRequest,
                    e.toString());
            return Response.newBuilder().setSuccess(false)
                    .setErrMsg(ClassUtils.getSimplaName(e) + ":" + ExceptionUtil.getCause(e).getMessage()).build();
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public Response onApply(WriteRequest log) {
        LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "onApply info : log : {}", log);
        final ByteString byteString = log.getData();
        Preconditions.checkArgument(byteString != null, "Log.getData() must not null");
        List<ModifyRequest> sqlContext = serializer.deserialize(byteString.toByteArray(), List.class);
        final Lock lock = readLock;
        lock.lock();
        try {
            boolean isOk = false;
            if (log.containsExtendInfo(DATA_IMPORT_KEY)) {
                isOk = doDataImport(jdbcTemplate, sqlContext);
            } else {
                sqlContext.sort(Comparator.comparingInt(ModifyRequest::getExecuteNo));
                isOk = update(transactionTemplate, jdbcTemplate, sqlContext);
                // If there is additional information, post processing
                // Put into the asynchronous thread pool for processing to avoid blocking the
                // normal execution of the state machine
                ConfigExecutor.executeEmbeddedDump(() -> handleExtendInfo(log.getExtendInfoMap()));
            }
            
            return Response.newBuilder().setSuccess(isOk).build();
            
            // We do not believe that an error caused by a problem with an SQL error
            // should trigger the stop operation of the raft state machine
        } catch (BadSqlGrammarException | DataIntegrityViolationException e) {
            return Response.newBuilder().setSuccess(false).setErrMsg(e.toString()).build();
        } catch (DataAccessException e) {
            throw new ConsistencyException(e.toString());
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void onError(Throwable throwable) {
        // Trigger reversion strategy
        NotifyCenter.publishEvent(new RaftDbErrorEvent(throwable));
    }
    
    @Override
    public String group() {
        return Constants.CONFIG_MODEL_RAFT_GROUP;
    }
    
    private void handleExtendInfo(Map<String, String> extendInfo) {
        if (extendInfo.containsKey(Constants.EXTEND_INFO_CONFIG_DUMP_EVENT)) {
            String jsonVal = extendInfo.get(Constants.EXTEND_INFO_CONFIG_DUMP_EVENT);
            if (StringUtils.isNotBlank(jsonVal)) {
                NotifyCenter.publishEvent(JacksonUtils.toObj(jsonVal, ConfigDumpEvent.class));
            }
            return;
        }
        if (extendInfo.containsKey(Constants.EXTEND_INFOS_CONFIG_DUMP_EVENT)) {
            String jsonVal = extendInfo.get(Constants.EXTEND_INFO_CONFIG_DUMP_EVENT);
            if (StringUtils.isNotBlank(jsonVal)) {
                List<ConfigDumpEvent> list = JacksonUtils.toObj(jsonVal, new GenericType<List<ConfigDumpEvent>>() {
                }.getType());
                list.stream().filter(Objects::nonNull).forEach(NotifyCenter::publishEvent);
            }
        }
    }
}
