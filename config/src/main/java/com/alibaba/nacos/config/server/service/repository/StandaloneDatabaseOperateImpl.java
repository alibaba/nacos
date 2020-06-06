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

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.config.server.configuration.ConditionStandaloneEmbedStorage;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.consistency.entity.Response;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(ConditionStandaloneEmbedStorage.class)
@Component
public class StandaloneDatabaseOperateImpl implements BaseDatabaseOperate {

	private DataSourceService dataSourceService;

	private JdbcTemplate jdbcTemplate;
	private TransactionTemplate transactionTemplate;

	@PostConstruct
	protected void init() {
		dataSourceService = DynamicDataSource.getInstance().getDataSource();
		jdbcTemplate = dataSourceService.getJdbcTemplate();
		transactionTemplate = dataSourceService.getTransactionTemplate();
		LogUtil.defaultLog.info("use StandaloneDatabaseOperateImpl");
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
	public CompletableFuture<RestResult<String>> dataImport(MultipartFile file) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				final File tmpFile = File.createTempFile(file.getName(), ".tmp");
				LineIterator iterator = FileUtils.lineIterator(tmpFile);
				int batchSize = 1000;
				List<String> batchUpdate = new ArrayList<>(batchSize);
				List<CompletableFuture<Boolean>> futures = new ArrayList<>();
				while (iterator.hasNext()) {
					String sql = iterator.next();
					batchUpdate.add(sql);
					if (batchUpdate.size() == batchSize ||!iterator.hasNext()) {
						futures.add(CompletableFuture.supplyAsync(() -> dataImport(jdbcTemplate, batchUpdate.stream()
								.map(s -> {
									ModifyRequest request = new ModifyRequest();
									request.setSql(s);
									return request;
								}).collect(Collectors.toList()))));
						batchUpdate.clear();
					}
				}
				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
				return RestResultUtils.success();
			} catch (Throwable ex) {
				return RestResultUtils.failed(ex.getMessage());
			}
		});
	}

	@Override
	public Boolean update(List<ModifyRequest> modifyRequests,
			BiConsumer<Boolean, Throwable> consumer) {
		return update(modifyRequests);
	}

	@Override
	public Boolean update(List<ModifyRequest> requestList) {
		return update(transactionTemplate, jdbcTemplate, requestList);
	}
}
