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
import com.alibaba.nacos.common.SerializeFactory;
import com.alibaba.nacos.common.Serializer;
import com.alibaba.nacos.common.utils.ClassUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.exception.NJdbcException;
import com.alibaba.nacos.config.server.service.DataSourceService;
import com.alibaba.nacos.config.server.service.DynamicDataSource;
import com.alibaba.nacos.config.server.service.RowMapperManager;
import com.alibaba.nacos.config.server.utils.MD5;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.cluster.MemberManager;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;

/**
 * // TODO 需不需要将JDBCException根据透传的originExceptionName进行构造
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
@Conditional(ConditionOnEmbedStoreType.class)
@Component
public class DistributedDatabaseOperateImpl extends BaseDatabaseOperate implements LogProcessor4CP, DatabaseOperate {

    private static final TypeReference<List<Pair<String, Object[]>>> reference = new TypeReference<List<Pair<String, Object[]>>>() {
    };

    @Autowired
    private DynamicDataSource dynamicDataSource;

    @Autowired
    private MemberManager memberManager;

    private DataSourceService dataSourceService;

    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;

    private ConsistencyProtocol<? extends Config> protocol;

    private Serializer serializer = SerializeFactory.getDefault();

    private String selfIp;

    @PostConstruct
    protected void init() throws Exception {
        dataSourceService = dynamicDataSource.getDataSource();
        jdbcTemplate = dataSourceService.getJdbcTemplate();
        transactionTemplate = dataSourceService.getTransactionTemplate();
        selfIp = memberManager.self().address();
        defaultLog.info("use DistributedTransactionServicesImpl");

        // Delete existing data, relying on raft's snapshot and log
        // playback to reply to the data is the correct behavior.

        // TODO Derby recreates the startup

        dataSourceService.destroyThenReload();
    }

    @Override
    public <R> R queryOne(String sql, Class<R> cls) {
        GetResponse<R> response;
        try {
            response = protocol.getData(GetRequest.builder()
                    .biz(bizInfo())
                    .ctx(serializer.serialize(SelectRequest
                            .builder()
                            .queryOne(true)
                            .useMapper(false)
                            .sql(sql)
                            .className(cls.getCanonicalName())
                            .build()))
                    .build());
        } catch (Exception e) {
            throw new NJdbcException(e);
        }
        if (response.success()) {
            return response.getData();
        }
        throw new NJdbcException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public <R> R queryOne(String sql, Object[] args, Class<R> cls) {
        GetResponse<R> response;
        try {
            response = protocol.getData(GetRequest.builder()
                    .biz(bizInfo())
                    .ctx(serializer.serialize(SelectRequest
                            .builder()
                            .queryOne(true)
                            .useMapper(false)
                            .sql(sql)
                            .args(args)
                            .className(cls.getCanonicalName())
                            .build()))
                    .build());
        } catch (Exception e) {
            throw new NJdbcException(e);
        }
        if (response.success()) {
            return response.getData();
        }
        throw new NJdbcException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public <R> R queryOne(String sql, Object[] args, RowMapper<R> mapper) {
        GetResponse<R> response;
        try {
            response = protocol.getData(GetRequest.builder()
                    .biz(bizInfo())
                    .ctx(serializer.serialize(SelectRequest
                            .builder()
                            .queryOne(true)
                            .useMapper(true)
                            .sql(sql)
                            .args(args)
                            .className(mapper.getClass().getCanonicalName())
                            .build()))
                    .build());
        } catch (Exception e) {
            throw new NJdbcException(e);
        }
        if (response.success()) {
            return response.getData();
        }
        throw new NJdbcException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public <R> List<R> queryMany(String sql, Object[] args, RowMapper<R> mapper) {
        GetResponse<List<R>> response;
        try {
            response = protocol.getData(GetRequest.builder()
                    .biz(bizInfo())
                    .ctx(serializer.serialize(SelectRequest
                            .builder()
                            .queryOne(false)
                            .useMapper(true)
                            .sql(sql)
                            .args(args)
                            .className(mapper.getClass().getCanonicalName())
                            .build()))
                    .build());
        } catch (Exception e) {
            throw new NJdbcException(e);
        }
        if (response.success()) {
            return response.getData();
        }
        throw new NJdbcException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public <R> List<R> queryMany(String sql, Object[] args, Class<R> rClass) {
        GetResponse<List<R>> response;
        try {
            response = protocol.getData(GetRequest.builder()
                    .biz(bizInfo())
                    .ctx(serializer.serialize(SelectRequest
                            .builder()
                            .queryOne(false)
                            .useMapper(false)
                            .sql(sql)
                            .args(args)
                            .className(rClass.getCanonicalName())
                            .build()))
                    .build());
        } catch (Exception e) {
            throw new NJdbcException(e);
        }
        if (response.success()) {
            return response.getData();
        }
        throw new NJdbcException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public List<Map<String, Object>> queryMany(String sql, Object[] args) {
        GetResponse<List<Map<String, Object>>> response;
        try {
            response = protocol.getData(GetRequest.builder()
                    .biz(bizInfo())
                    .ctx(serializer.serialize(SelectRequest
                            .builder()
                            .queryOne(false)
                            .useMapper(false)
                            .sql(sql)
                            .args(args)
                            .className(null)
                            .build()))
                    .build());
        } catch (Exception e) {
            throw new NJdbcException(e);
        }
        if (response.success()) {
            return response.getData();
        }
        throw new NJdbcException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public Boolean update(List<SQL> sqlContext) {
        try {

            byte[] data = serializer.serialize(sqlContext);

            final String key = bizInfo() + "-" + selfIp + "-" + MD5.getInstance().getMD5String(data);

            return protocol.submit(NLog.builder()
                    .biz(bizInfo())
                    .key(key)
                    .data(data)
                    .className(List.class.getCanonicalName())
                    .build());
        } catch (Exception e) {
            throw new NJdbcException(e);
        }
    }

    @Override
    public void injectProtocol(ConsistencyProtocol<? extends Config> protocol) {
        this.protocol = protocol;
    }

    @Override
    public ConsistencyProtocol<? extends Config> getProtocol() {
        return protocol;
    }

    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new DerbySnapshotOperation());
    }

    @SuppressWarnings("all")
    @Override
    public <R> GetResponse<R> getData(GetRequest request) {
        SelectRequest selectRequest = serializer.deSerialize(request.getCtx(), SelectRequest.class);
        RowMapper<Object> mapper = RowMapperManager.getRowMapper(selectRequest.getClassName());

        // TODO 需要优化下

        GetResponse<R> response = new GetResponse<>();
        try {
            if (selectRequest.isQueryOne()) {
                if (selectRequest.isUseMapper()) {
                    response.setData((R) onQueryOne(selectRequest.getSql(), selectRequest.getArgs(), mapper));
                    return response;
                }
                if (selectRequest.getArgs() == null || selectRequest.getArgs().length == 0) {
                    response.setData((R) onQueryOne(selectRequest.getSql(),
                            ClassUtils.findClassByName(selectRequest.getClassName())));
                    return response;
                }
                response.setData((R) onQueryOne(selectRequest.getSql(), selectRequest.getArgs(),
                        ClassUtils.findClassByName(selectRequest.getClassName())));
                return response;
            }
            if (selectRequest.isUseMapper()) {
                response.setData((R) onQueryMany(selectRequest.getSql(), selectRequest.getArgs(), mapper));
                return response;
            }
            if (StringUtils.isBlank(selectRequest.getClassName())) {
                response.setData((R) onQueryMany(selectRequest.getSql(), selectRequest.getArgs()));
                return response;
            }
            response.setData((R) onQueryMany(selectRequest.getSql(), selectRequest.getArgs(),
                    ClassUtils.findClassByName(selectRequest.getClassName())));
            return response;
        } catch (Exception e) {
            response.setExceptionName(e.getClass().getName());
            response.setExceptionName(e.getMessage());
            return response;
        }
    }

    @Override
    public boolean onApply(Log log) {
        List<SQL> sqlContext = serializer.deSerialize(log.getData(), List.class);
        return onUpdate(sqlContext);
    }

    @Override
    public String bizInfo() {
        return Constants.CONFIG_MODEL_RAFT_GROUP;
    }

    private Boolean onUpdate(List<SQL> sqlContext) {
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
