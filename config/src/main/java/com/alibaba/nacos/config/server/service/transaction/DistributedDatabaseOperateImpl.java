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

package com.alibaba.nacos.config.server.service.transaction;

import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.config.server.model.event.RaftDBErrorEvent;
import com.alibaba.nacos.config.server.service.LocalDataSourceServiceImpl;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.consistency.LogFuture;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.common.utils.ClassUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.exception.NJdbcException;
import com.alibaba.nacos.config.server.service.DynamicDataSource;
import com.alibaba.nacos.config.server.service.RowMapperManager;
import com.alibaba.nacos.config.server.utils.MD5;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.GetResponse;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.cluster.MemberManager;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.PostConstruct;

import com.alibaba.nacos.core.notify.NotifyCenter;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
@Conditional(ConditionOnEmbedStoreType.class)
@Component
public class DistributedDatabaseOperateImpl extends LogProcessor4CP implements BaseDatabaseOperate, DatabaseOperate {

    private static final TypeReference<List<Pair<String, Object[]>>> reference = new TypeReference<List<Pair<String, Object[]>>>() {
    };

    @Autowired
    private MemberManager memberManager;

    private LocalDataSourceServiceImpl dataSourceService;
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private Serializer serializer = SerializeFactory.getDefault();
    private String selfIp;
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    @PostConstruct
    protected void init() throws Exception {
        dataSourceService = (LocalDataSourceServiceImpl) DynamicDataSource.getInstance().getDataSource();
        dataSourceService.cleanAndReopenDerby();
        jdbcTemplate = dataSourceService.getJdbcTemplate();
        transactionTemplate = dataSourceService.getTransactionTemplate();
        selfIp = memberManager.getSelf().getAddress();
        NotifyCenter.registerPublisher(RaftDBErrorEvent::new, RaftDBErrorEvent.class);
        defaultLog.info("use DistributedTransactionServicesImpl");
    }

    @Override
    public <R> R queryOne(String sql, Class<R> cls) {
        try {
            byte[] data = serializer.serialize(SelectRequest
                    .builder()
                    .queryType(QueryType.QUERY_ONE_NO_MAPPER_NO_ARGS)
                    .sql(sql)
                    .className(cls.getCanonicalName())
                    .build());

            GetResponse response = protocol.getData(GetRequest.newBuilder()
                    .setGroup(group())
                    .setData(ByteString.copyFrom(data))
                    .build());
            if (StringUtils.isEmpty(response.getErrMsg())) {
                return serializer.deserialize(response.getData().toByteArray(), cls);
            }
            throw new NJdbcException(response.getErrMsg(), response.getErrMsg());
        } catch (Exception e) {
            LogUtil.fatalLog.error("An exception occurred during the query operation : {}", e);
            throw new NJdbcException(e);
        }
    }

    @Override
    public <R> R queryOne(String sql, Object[] args, Class<R> cls) {
        try {
            byte[] data = serializer.serialize(SelectRequest
                    .builder()
                    .queryType(QueryType.QUERY_ONE_NO_MAPPER_WITH_ARGS)
                    .sql(sql)
                    .args(args)
                    .className(cls.getCanonicalName())
                    .build());

            GetResponse response = protocol.getData(GetRequest.newBuilder()
                    .setGroup(group())
                    .setData(ByteString.copyFrom(data))
                    .build());
            if (StringUtils.isEmpty(response.getErrMsg())) {
                return serializer.deserialize(response.getData().toByteArray(), cls);
            }
            throw new NJdbcException(response.getErrMsg(), response.getErrMsg());
        } catch (Exception e) {
            LogUtil.fatalLog.error("An exception occurred during the query operation : {}", e);
            throw new NJdbcException(e);
        }
    }

    @Override
    public <R> R queryOne(String sql, Object[] args, RowMapper<R> mapper) {
        try {
            byte[] data = serializer.serialize(SelectRequest
                    .builder()
                    .queryType(QueryType.QUERY_ONE_WITH_MAPPER_WITH_ARGS)
                    .sql(sql)
                    .args(args)
                    .className(mapper.getClass().getCanonicalName())
                    .build());

            GetResponse response = protocol.getData(GetRequest.newBuilder()
                    .setGroup(group())
                    .setData(ByteString.copyFrom(data))
                    .build());
            if (StringUtils.isEmpty(response.getErrMsg())) {
                return serializer.deserialize(response.getData().toByteArray(), ClassUtils.resolveGenericTypeByInterface(mapper.getClass()));
            }
            throw new NJdbcException(response.getErrMsg(), response.getErrMsg());
        } catch (Exception e) {
            LogUtil.fatalLog.error("An exception occurred during the query operation : {}", e);
            throw new NJdbcException(e);
        }
    }

    @Override
    public <R> List<R> queryMany(String sql, Object[] args, RowMapper<R> mapper) {
        try {

            byte[] data = serializer.serialize(SelectRequest
                    .builder()
                    .queryType(QueryType.QUERY_MANY_WITH_MAPPER_WITH_ARGS)
                    .sql(sql)
                    .args(args)
                    .className(mapper.getClass().getCanonicalName())
                    .build());

            GetResponse response = protocol.getData(GetRequest.newBuilder()
                    .setGroup(group())
                    .setData(ByteString.copyFrom(data))
                    .build());
            if (StringUtils.isEmpty(response.getErrMsg())) {
                return serializer.deserialize(response.getData().toByteArray(), List.class);
            }
            throw new NJdbcException(response.getErrMsg());
        } catch (Exception e) {
            LogUtil.fatalLog.error("An exception occurred during the query operation : {}", e);
            throw new NJdbcException(e);
        }
    }

    @Override
    public <R> List<R> queryMany(String sql, Object[] args, Class<R> rClass) {
        try {
            byte[] data = serializer.serialize(SelectRequest
                    .builder()
                    .queryType(QueryType.QUERY_MANY_NO_MAPPER_WITH_ARGS)
                    .sql(sql)
                    .args(args)
                    .className(rClass.getCanonicalName())
                    .build());
            GetResponse response = protocol.getData(GetRequest.newBuilder()
                    .setGroup(group())
                    .setData(ByteString.copyFrom(data))
                    .build());
            if (StringUtils.isEmpty(response.getErrMsg())) {
                return serializer.deserialize(response.getData().toByteArray(), List.class);
            }
            throw new NJdbcException(response.getErrMsg());
        } catch (Exception e) {
            LogUtil.fatalLog.error("An exception occurred during the query operation : {}", e);
            throw new NJdbcException(e);
        }
    }

    @Override
    public List<Map<String, Object>> queryMany(String sql, Object[] args) {
        try {
            byte[] data = serializer.serialize(SelectRequest
                    .builder()
                    .queryType(QueryType.QUERY_MANY_WITH_LIST_WITH_ARGS)
                    .sql(sql)
                    .args(args)
                    .build());

            GetResponse response = protocol.getData(GetRequest.newBuilder()
                    .setGroup(group())
                    .setData(ByteString.copyFrom(data))
                    .build());
            if (StringUtils.isEmpty(response.getErrMsg())) {
                return serializer.deserialize(response.getData().toByteArray(), List.class);
            }
            throw new NJdbcException(response.getErrMsg());
        } catch (Exception e) {
            LogUtil.fatalLog.error("An exception occurred during the query operation : {}", e);
            throw new NJdbcException(e);
        }
    }

    @Override
    public Boolean update(List<ModifyRequest> sqlContext) {
        try {
            final String key = group() + "-" + selfIp + "-" + MD5.getInstance().getMD5String(sqlContext.toString());
            Log log = Log.newBuilder()
                    .setKey(key)
                    .setData(ByteString.copyFrom(serializer.serialize(sqlContext)))
                    .setType(sqlContext.getClass().getCanonicalName())
                    .build();
            LogFuture future = commitAutoSetGroup(log);
            if (future.isOk()) {
                return true;
            }
            throw future.getError();
        } catch (Throwable e) {
            LogUtil.fatalLog.error("An exception occurred during the update operation : {}", e);
            throw new NJdbcException(e);
        }
    }

    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new DerbySnapshotOperation(writeLock));
    }

    @SuppressWarnings("all")
    @Override
    public GetResponse getData(final GetRequest request) {
        final SelectRequest selectRequest = serializer.deserialize(request.getData().toByteArray(), SelectRequest.class);
        final RowMapper<Object> mapper = RowMapperManager.getRowMapper(selectRequest.getClassName());
        final byte type = selectRequest.getQueryType();
        readLock.lock();
        Object data;
        try {
            switch (type) {
            case QueryType.QUERY_ONE_WITH_MAPPER_WITH_ARGS:
                data = onQueryOne(selectRequest.getSql(), selectRequest.getArgs(), mapper);
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
                data = onQueryMany(selectRequest.getSql(), selectRequest.getArgs(), mapper);
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
            return GetResponse.newBuilder().setData(ByteString.copyFrom(serializer.serialize(data))).build();
        } catch (Exception e) {
            LogUtil.fatalLog.error("There was an error querying the data, request : {}, error : {}", selectRequest, e);
            return GetResponse.newBuilder().setErrMsg(e.getClass().getSimpleName() + ":" + e.getMessage()).build();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public LogFuture onApply(Log log) {
        List<ModifyRequest> sqlContext = serializer.deserialize(log.getData().toByteArray(), List.class);
        readLock.lock();
        try {
            Collections.sort(sqlContext, new Comparator<ModifyRequest>() {
                @Override public int compare(ModifyRequest pre, ModifyRequest next) {
                    return pre.getExecuteNo() - next.getExecuteNo();
                }
            });
            boolean isOk = onUpdate(sqlContext);
            return LogFuture.success(isOk);

            // We do not believe that an error caused by a problem with an SQL error
            // should trigger the stop operation of the raft state machine

        } catch (DuplicateKeyException e) {
            return LogFuture.fail(e);
        } catch (DataIntegrityViolationException e) {
            return LogFuture.fail(e);
        } catch (BadSqlGrammarException e) {
            return LogFuture.fail(e);
        } catch (Throwable t) {
            throw new ConsistencyException(t);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void onError(Throwable throwable) {

        // Trigger reversion strategy

        NotifyCenter.publishEvent(new RaftDBErrorEvent());
    }

    @Override
    public String group() {
        return Constants.CONFIG_MODEL_RAFT_GROUP;
    }

    private Boolean onUpdate(List<ModifyRequest> sqlContext) {
        return update(transactionTemplate, jdbcTemplate, sqlContext);
    }

    private <R> R onQueryOne(String sql, Class<R> rClass) {
        return queryOne(jdbcTemplate, sql, rClass);
    }

    private <R> R onQueryOne(String sql, Object[] args, Class<R> rClass) {
        return queryOne(jdbcTemplate, sql, args, rClass);
    }

    private <R> R onQueryOne(String sql, Object[] args, RowMapper<R> mapper) {
        return queryOne(jdbcTemplate, sql, args, mapper);
    }

    private <R> List<R> onQueryMany(String sql, Object[] args, RowMapper<R> mapper) {
        return queryMany(jdbcTemplate, sql, args, mapper);
    }

    private <R> List<R> onQueryMany(String sql, Object[] args, Class<R> rClass) {
        return queryMany(jdbcTemplate, sql, args, rClass);
    }

    private List<Map<String, Object>> onQueryMany(String sql, Object[] args) {
        return queryMany(jdbcTemplate, sql, args);
    }
}
