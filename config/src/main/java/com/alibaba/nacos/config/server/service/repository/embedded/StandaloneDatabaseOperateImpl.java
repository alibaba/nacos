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

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionStandaloneEmbedStorage;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Derby operation in stand-alone mode.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(ConditionStandaloneEmbedStorage.class)
@Component
public class StandaloneDatabaseOperateImpl implements BaseDatabaseOperate {
    
    private JdbcTemplate jdbcTemplate;
    
    private TransactionTemplate transactionTemplate;
    
    @PostConstruct
    protected void init() {
        DataSourceService dataSourceService = DynamicDataSource.getInstance().getDataSource();
        jdbcTemplate = dataSourceService.getJdbcTemplate();
        transactionTemplate = dataSourceService.getTransactionTemplate();
        LogUtil.DEFAULT_LOG.info("use StandaloneDatabaseOperateImpl");
    }
    
    @Override
    public <R> R queryOne(String sql, Class<R> cls) {
        return queryOne(jdbcTemplate, sql, cls);
    }
    
    @Override
    public <R> R queryOne(String sql, Object[] args, Class<R> cls) {
        return queryOne(jdbcTemplate, sql, args, cls);
    }
    
    @Override
    public <R> R queryOne(String sql, Object[] args, RowMapper<R> mapper) {
        return queryOne(jdbcTemplate, sql, args, mapper);
    }
    
    @Override
    public <R> List<R> queryMany(String sql, Object[] args, RowMapper<R> mapper) {
        return queryMany(jdbcTemplate, sql, args, mapper);
    }
    
    @Override
    public <R> List<R> queryMany(String sql, Object[] args, Class<R> rClass) {
        return queryMany(jdbcTemplate, sql, args, rClass);
    }
    
    @Override
    public List<Map<String, Object>> queryMany(String sql, Object[] args) {
        return queryMany(jdbcTemplate, sql, args);
    }
    
    @Override
    public CompletableFuture<RestResult<String>> dataImport(File file) {
        return CompletableFuture.supplyAsync(() -> {
            try (DiskUtils.LineIterator iterator = DiskUtils.lineIterator(file)) {
                int batchSize = 1000;
                List<String> batchUpdate = new ArrayList<>(batchSize);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                List<Boolean> results = new CopyOnWriteArrayList<>();
                while (iterator.hasNext()) {
                    String sql = iterator.next();
                    if (StringUtils.isNotBlank(sql)) {
                        batchUpdate.add(sql);
                    }
                    if (batchUpdate.size() == batchSize || !iterator.hasNext()) {
                        List<ModifyRequest> sqls = batchUpdate.stream().map(s -> {
                            ModifyRequest request = new ModifyRequest();
                            request.setSql(s);
                            return request;
                        }).collect(Collectors.toList());
                        futures.add(CompletableFuture.runAsync(() -> results.add(doDataImport(jdbcTemplate, sqls))));
                        batchUpdate.clear();
                    }
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                return RestResult.<String>builder()
                        .withCode(BooleanUtils.and(results.toArray(new Boolean[0])) ? 200 : 500).withData("").build();
            } catch (Throwable ex) {
                LogUtil.DEFAULT_LOG.error("An exception occurred when external data was imported into Derby : ", ex);
                return RestResultUtils.failed(ex.getMessage());
            }
        });
    }
    
    @Override
    public Boolean update(List<ModifyRequest> modifyRequests, BiConsumer<Boolean, Throwable> consumer) {
        return update(transactionTemplate, jdbcTemplate, modifyRequests, consumer);
    }
    
    @Override
    public Boolean update(List<ModifyRequest> requestList) {
        return update(transactionTemplate, jdbcTemplate, requestList);
    }
}
