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

package com.alibaba.nacos.config.server.service.repository;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionDistributedEmbedStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.exception.NJdbcException;
import com.alibaba.nacos.config.server.model.event.ConfigDumpEvent;
import com.alibaba.nacos.config.server.model.event.RaftDBErrorEvent;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.config.server.service.dump.DumpConfigHandler;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import com.alibaba.nacos.config.server.service.sql.QueryType;
import com.alibaba.nacos.config.server.service.sql.SelectRequest;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.ClassUtils;
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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

/**
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
 *               │                   │                  3:onApply         2:submit(List<ModifyRequest>)
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
@SuppressWarnings("all")
@Conditional(ConditionDistributedEmbedStorage.class)
@Component
public class DistributedDatabaseOperateImpl extends LogProcessor4CP
		implements BaseDatabaseOperate, DatabaseOperate {

	private ServerMemberManager memberManager;
	private CPProtocol protocol;

	private LocalDataSourceServiceImpl dataSourceService;
	private JdbcTemplate jdbcTemplate;
	private TransactionTemplate transactionTemplate;
	private Serializer serializer = SerializeFactory.getDefault();
	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
	private ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

	public DistributedDatabaseOperateImpl(ServerMemberManager memberManager,
			ProtocolManager protocolManager) throws Exception {
		this.memberManager = memberManager;
		this.protocol = protocolManager.getCpProtocol();
		init();
	}

	protected void init() throws Exception {

		this.dataSourceService = (LocalDataSourceServiceImpl) DynamicDataSource
				.getInstance().getDataSource();

		// Because in Raft + Derby mode, ensuring data consistency depends on the Raft's
		// log playback and snapshot recovery capabilities, and the last data must be cleared
		this.dataSourceService.cleanAndReopenDerby();

		this.jdbcTemplate = dataSourceService.getJdbcTemplate();
		this.transactionTemplate = dataSourceService.getTransactionTemplate();

		// Registers a Derby Raft state machine failure event for node degradation processing
		NotifyCenter.registerToSharePublisher(RaftDBErrorEvent.class);
		// Register the snapshot load event
		NotifyCenter.registerToSharePublisher(DerbyLoadEvent.class);

		NotifyCenter.registerSubscribe(new Subscribe<RaftDBErrorEvent>() {
			@Override
			public void onEvent(RaftDBErrorEvent event) {
				dataSourceService.setHealthStatus("DOWN");
			}

			@Override
			public Class<? extends Event> subscribeType() {
				return RaftDBErrorEvent.class;
			}
		});

		NotifyCenter.registerToPublisher(ConfigDumpEvent.class,
				NotifyCenter.RING_BUFFER_SIZE);
		NotifyCenter.registerSubscribe(new DumpConfigHandler());

		this.protocol.addLogProcessors(Collections.singletonList(this));
		LogUtil.defaultLog.info("use DistributedTransactionServicesImpl");
	}

	@JustForTest
	public void mockConsistencyProtocol(CPProtocol protocol) {
		this.protocol = protocol;
	}

	@Override
	public <R> R queryOne(String sql, Class<R> cls) {
		try {
			LoggerUtils
					.printIfDebugEnabled(LogUtil.defaultLog, "queryOne info : sql : {}",
							sql);

			byte[] data = serializer.serialize(SelectRequest.builder()
					.queryType(QueryType.QUERY_ONE_NO_MAPPER_NO_ARGS).sql(sql)
					.className(cls.getCanonicalName()).build());

			final boolean blockRead = EmbeddedStorageContextUtils.containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);

			Response response = innerRead(GetRequest.newBuilder().setGroup(group())
					.setData(ByteString.copyFrom(data))
					.build(), blockRead);
			if (response.getSuccess()) {
				return serializer.deserialize(response.getData().toByteArray(), cls);
			}
			throw new NJdbcException(response.getErrMsg(), response.getErrMsg());
		}
		catch (Exception e) {
			LogUtil.fatalLog
					.error("An exception occurred during the query operation : {}",
							e.toString());
			throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
		}
	}

	@Override
	public <R> R queryOne(String sql, Object[] args, Class<R> cls) {
		try {
			LoggerUtils.printIfDebugEnabled(LogUtil.defaultLog,
					"queryOne info : sql : {}, args : {}", sql, args);

			byte[] data = serializer.serialize(SelectRequest.builder()
					.queryType(QueryType.QUERY_ONE_NO_MAPPER_WITH_ARGS).sql(sql)
					.args(args).className(cls.getCanonicalName()).build());

			final boolean blockRead = EmbeddedStorageContextUtils.containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);

			Response response = innerRead(GetRequest.newBuilder().setGroup(group())
					.setData(ByteString.copyFrom(data))
					.build(), blockRead);
			if (response.getSuccess()) {
				return serializer.deserialize(response.getData().toByteArray(), cls);
			}
			throw new NJdbcException(response.getErrMsg(), response.getErrMsg());
		}
		catch (Exception e) {
			LogUtil.fatalLog
					.error("An exception occurred during the query operation : {}",
							e.toString());
			throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
		}
	}

	@Override
	public <R> R queryOne(String sql, Object[] args, RowMapper<R> mapper) {
		try {
			LoggerUtils.printIfDebugEnabled(LogUtil.defaultLog,
					"queryOne info : sql : {}, args : {}", sql, args);

			byte[] data = serializer.serialize(SelectRequest.builder()
					.queryType(QueryType.QUERY_ONE_WITH_MAPPER_WITH_ARGS).sql(sql)
					.args(args).className(mapper.getClass().getCanonicalName()).build());

			final boolean blockRead = EmbeddedStorageContextUtils.containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);

			Response response = innerRead(GetRequest.newBuilder().setGroup(group())
					.setData(ByteString.copyFrom(data))
					.build(), blockRead);
			if (response.getSuccess()) {
				return serializer.deserialize(response.getData().toByteArray(),
						ClassUtils.resolveGenericTypeByInterface(mapper.getClass()));
			}
			throw new NJdbcException(response.getErrMsg(), response.getErrMsg());
		}
		catch (Exception e) {
			LogUtil.fatalLog
					.error("An exception occurred during the query operation : {}",
							e.toString());
			throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
		}
	}

	@Override
	public <R> List<R> queryMany(String sql, Object[] args, RowMapper<R> mapper) {
		try {
			LoggerUtils.printIfDebugEnabled(LogUtil.defaultLog,
					"queryMany info : sql : {}, args : {}", sql, args);

			byte[] data = serializer.serialize(SelectRequest.builder()
					.queryType(QueryType.QUERY_MANY_WITH_MAPPER_WITH_ARGS).sql(sql)
					.args(args).className(mapper.getClass().getCanonicalName()).build());

			final boolean blockRead = EmbeddedStorageContextUtils.containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);

			Response response = innerRead(GetRequest.newBuilder().setGroup(group())
					.setData(ByteString.copyFrom(data))
					.build(), blockRead);
			if (response.getSuccess()) {
				return serializer
						.deserialize(response.getData().toByteArray(), List.class);
			}
			throw new NJdbcException(response.getErrMsg());
		}
		catch (Exception e) {
			LogUtil.fatalLog
					.error("An exception occurred during the query operation : {}",
							e.toString());
			throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
		}
	}

	@Override
	public <R> List<R> queryMany(String sql, Object[] args, Class<R> rClass) {
		try {
			LoggerUtils.printIfDebugEnabled(LogUtil.defaultLog,
					"queryMany info : sql : {}, args : {}", sql, args);

			byte[] data = serializer.serialize(SelectRequest.builder()
					.queryType(QueryType.QUERY_MANY_NO_MAPPER_WITH_ARGS).sql(sql)
					.args(args).className(rClass.getCanonicalName()).build());

			final boolean blockRead = EmbeddedStorageContextUtils.containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);

			Response response = innerRead(GetRequest.newBuilder().setGroup(group())
					.setData(ByteString.copyFrom(data))
					.build(), blockRead);
			if (response.getSuccess()) {
				return serializer
						.deserialize(response.getData().toByteArray(), List.class);
			}
			throw new NJdbcException(response.getErrMsg());
		}
		catch (Exception e) {
			LogUtil.fatalLog
					.error("An exception occurred during the query operation : {}",
							e.toString());
			throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
		}
	}

	@Override
	public List<Map<String, Object>> queryMany(String sql, Object[] args) {
		try {
			LoggerUtils.printIfDebugEnabled(LogUtil.defaultLog,
					"queryMany info : sql : {}, args : {}", sql, args);

			byte[] data = serializer.serialize(SelectRequest.builder()
					.queryType(QueryType.QUERY_MANY_WITH_LIST_WITH_ARGS).sql(sql)
					.args(args).build());

			final boolean blockRead = EmbeddedStorageContextUtils.containsExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA);

			Response response = innerRead(GetRequest.newBuilder().setGroup(group())
					.setData(ByteString.copyFrom(data))
					.build(), blockRead);
			if (response.getSuccess()) {
				return serializer
						.deserialize(response.getData().toByteArray(), List.class);
			}
			throw new NJdbcException(response.getErrMsg());
		}
		catch (Exception e) {
			LogUtil.fatalLog
					.error("An exception occurred during the query operation : {}",
							e.toString());
			throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
		}
	}

	/**
	 * In some business situations, you need to avoid the timeout issue, so blockRead is used to determine this
	 *
	 * @param request {@link GetRequest}
	 * @param blockRead
	 * @return {@link Response}
	 * @throws Exception
	 */
	private Response innerRead(GetRequest request, boolean blockRead) throws Exception {
		if (blockRead) {
			return (Response) protocol.aGetData(request).join();
		}
		Response response = protocol.getData(request);
		return response;
	}

	@Override
	public Boolean update(List<ModifyRequest> sqlContext,
			BiConsumer<Boolean, Throwable> consumer) {
		try {

			// Since the SQL parameter is Object[], in order to ensure that the types of
			// array elements are not lost, the serialization here is done using the java-specific
			// serialization framework, rather than continuing with the protobuff

			LoggerUtils
					.printIfDebugEnabled(LogUtil.defaultLog, "modifyRequests info : {}",
							sqlContext);

			// {timestamp}-{group}-{ip:port}-{signature}

			final String key =
					System.currentTimeMillis() + "-" + group() + "-" + memberManager
							.getSelf().getAddress() + "-" + MD5Utils
							.md5Hex(sqlContext.toString(), Constants.ENCODE);
			Log log = Log.newBuilder().setGroup(group()).setKey(key)
					.setData(ByteString.copyFrom(serializer.serialize(sqlContext)))
					.putAllExtendInfo(EmbeddedStorageContextUtils.getCurrentExtendInfo())
					.setType(sqlContext.getClass().getCanonicalName()).build();
			if (Objects.isNull(consumer)) {
				Response response = this.protocol.submit(log);
				if (response.getSuccess()) {
					return true;
				}
				LogUtil.defaultLog.error("execute sql modify operation failed : {}",
						response.getErrMsg());
				return false;
			}
			else {
				this.protocol.submitAsync(log)
						.whenComplete(
								(BiConsumer<Response, Throwable>) (response, ex) -> {
									String errMsg = Objects.isNull(ex) ?
											response.getErrMsg() :
											ExceptionUtil.getCause(ex).getMessage();
									consumer.accept(response.getSuccess(),
											StringUtils.isBlank(errMsg) ?
													null :
													new NJdbcException(errMsg));
								});
			}
			return true;
		}
		catch (TimeoutException e) {
			LogUtil.fatalLog
					.error("An timeout exception occurred during the update operation");
			throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
		}
		catch (Throwable e) {
			LogUtil.fatalLog
					.error("An exception occurred during the update operation : {}", e);
			throw new NacosRuntimeException(NacosException.SERVER_ERROR, e.toString());
		}
	}

	@Override
	public List<SnapshotOperation> loadSnapshotOperate() {
		return Collections.singletonList(new DerbySnapshotOperation(writeLock));
	}

	@SuppressWarnings("all")
	@Override
	public Response onRequest(final GetRequest request) {
		final SelectRequest selectRequest = serializer
				.deserialize(request.getData().toByteArray(), SelectRequest.class);

		LoggerUtils.printIfDebugEnabled(LogUtil.defaultLog,
				"getData info : selectRequest : {}", selectRequest);

		final RowMapper<Object> mapper = RowMapperManager
				.getRowMapper(selectRequest.getClassName());
		final byte type = selectRequest.getQueryType();
		readLock.lock();
		Object data;
		try {
			switch (type) {
			case QueryType.QUERY_ONE_WITH_MAPPER_WITH_ARGS:
				data = onQueryOne(selectRequest.getSql(), selectRequest.getArgs(),
						mapper);
				break;
			case QueryType.QUERY_ONE_NO_MAPPER_NO_ARGS:
				data = onQueryOne(selectRequest.getSql(),
						ClassUtils.findClassByName(selectRequest.getClassName()));
				break;
			case QueryType.QUERY_ONE_NO_MAPPER_WITH_ARGS:
				data = onQueryOne(selectRequest.getSql(), selectRequest.getArgs(),
						ClassUtils.findClassByName(selectRequest.getClassName()));
				break;
			case QueryType.QUERY_MANY_WITH_MAPPER_WITH_ARGS:
				data = onQueryMany(selectRequest.getSql(), selectRequest.getArgs(),
						mapper);
				break;
			case QueryType.QUERY_MANY_WITH_LIST_WITH_ARGS:
				data = onQueryMany(selectRequest.getSql(), selectRequest.getArgs());
				break;
			case QueryType.QUERY_MANY_NO_MAPPER_WITH_ARGS:
				data = onQueryMany(selectRequest.getSql(), selectRequest.getArgs(),
						ClassUtils.findClassByName(selectRequest.getClassName()));
				break;
			default:
				throw new IllegalArgumentException("Unsupported data query categories");
			}
			ByteString bytes = data == null ?
					ByteString.EMPTY :
					ByteString.copyFrom(serializer.serialize(data));
			return Response.newBuilder().setSuccess(true).setData(bytes).build();
		}
		catch (Exception e) {
			LogUtil.fatalLog
					.error("There was an error querying the data, request : {}, error : {}",
							selectRequest, e.toString());
			return Response.newBuilder().setSuccess(false)
					.setErrMsg(ClassUtils.getSimplaName(e) + ":" + ExceptionUtil.getCause(e).getMessage())
					.build();
		}
		finally {
			readLock.unlock();
		}
	}

	@Override
	public Response onApply(Log log) {
		LoggerUtils
				.printIfDebugEnabled(LogUtil.defaultLog, "onApply info : log : {}", log);
		final ByteString byteString = log.getData();
		Preconditions.checkArgument(byteString != null, "Log.getData() must not null");
		List<ModifyRequest> sqlContext = serializer
				.deserialize(byteString.toByteArray(), List.class);
		final Lock lock = readLock;
		lock.lock();
		try {
			sqlContext.sort(Comparator.comparingInt(ModifyRequest::getExecuteNo));
			boolean isOk = onUpdate(sqlContext);

			// If there is additional information, post processing
			// Put into the asynchronous thread pool for processing to avoid blocking the
			// normal execution of the state machine
			ConfigExecutor
					.executeEmbeddedDump(() -> handleExtendInfo(log.getExtendInfoMap()));

			return Response.newBuilder().setSuccess(isOk).build();

			// We do not believe that an error caused by a problem with an SQL error
			// should trigger the stop operation of the raft state machine
		}
		catch (BadSqlGrammarException | DataIntegrityViolationException e) {
			return Response.newBuilder().setSuccess(false).setErrMsg(e.toString())
					.build();
		}
		catch (DataAccessException e) {
			throw new ConsistencyException(e.toString());
		}
		catch (Throwable t) {
			throw t;
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void onError(Throwable throwable) {
		// Trigger reversion strategy
		NotifyCenter.publishEvent(new RaftDBErrorEvent(throwable));
	}

	@Override
	public String group() {
		return Constants.CONFIG_MODEL_RAFT_GROUP;
	}

	public Boolean onUpdate(List<ModifyRequest> sqlContext) {
		return update(transactionTemplate, jdbcTemplate, sqlContext);
	}

	public <R> R onQueryOne(String sql, Class<R> rClass) {
		return queryOne(jdbcTemplate, sql, rClass);
	}

	public <R> R onQueryOne(String sql, Object[] args, Class<R> rClass) {
		return queryOne(jdbcTemplate, sql, args, rClass);
	}

	public <R> R onQueryOne(String sql, Object[] args, RowMapper<R> mapper) {
		return queryOne(jdbcTemplate, sql, args, mapper);
	}

	public <R> List<R> onQueryMany(String sql, Object[] args, RowMapper<R> mapper) {
		return queryMany(jdbcTemplate, sql, args, mapper);
	}

	public <R> List<R> onQueryMany(String sql, Object[] args, Class<R> rClass) {
		return queryMany(jdbcTemplate, sql, args, rClass);
	}

	public List<Map<String, Object>> onQueryMany(String sql, Object[] args) {
		return queryMany(jdbcTemplate, sql, args);
	}

	private void handleExtendInfo(Map<String, String> extendInfo) {
		if (extendInfo.containsKey(Constants.EXTEND_INFO_CONFIG_DUMP_EVENT)) {
			String jsonVal = extendInfo.get(Constants.EXTEND_INFO_CONFIG_DUMP_EVENT);
			if (StringUtils.isNotBlank(jsonVal)) {
				NotifyCenter
						.publishEvent(JacksonUtils.toObj(jsonVal, ConfigDumpEvent.class));
			}
			return;
		}
		if (extendInfo.containsKey(Constants.EXTEND_INFOS_CONFIG_DUMP_EVENT)) {
			String jsonVal = extendInfo.get(Constants.EXTEND_INFO_CONFIG_DUMP_EVENT);
			if (StringUtils.isNotBlank(jsonVal)) {
				List<ConfigDumpEvent> list = JacksonUtils
						.toObj(jsonVal, new GenericType<List<ConfigDumpEvent>>() {
						}.getType());
				list.stream().filter(Objects::nonNull)
						.forEach(NotifyCenter::publishEvent);
			}
		}
	}
}
