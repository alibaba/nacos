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

package com.alibaba.nacos.test.config;

import com.alibaba.nacos.config.server.service.repository.DistributedDatabaseOperateImpl;
import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import com.alibaba.nacos.consistency.LogFuture;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.sql.SQLException;
import java.util.List;

/**
 * If the SQL logic is wrong or the constraint is violated, the exception should not be
 * thrown, but the return should be carried in the LogFuture, otherwise it will be thrown
 * to the upper level
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class DerbyRaftError_ITCase {

	@Spy
	private DistributedDatabaseOperateImpl databaseOperate;

	private Serializer serializer = SerializeFactory.getDefault();

	@Before
	public void before() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void test_logic_DuplicateKeyException() {

		Mockito.doThrow(new DuplicateKeyException("DuplicateKeyException"))
				.when(databaseOperate).onUpdate(Mockito.anyList());

		List<ModifyRequest> list = Lists.newArrayList(new ModifyRequest());

		LogFuture future = databaseOperate.onApply(
				Log.newBuilder().setData(ByteString.copyFrom(serializer.serialize(list)))
						.build());

		Assert.assertEquals(future.getError().getMessage(), "DuplicateKeyException");
	}

	@Test
	public void test_logic_BadSqlGrammarException() {

		Mockito.doThrow(new BadSqlGrammarException("BadSqlGrammarException",
				"BadSqlGrammarException", new SQLException())).when(databaseOperate)
				.onUpdate(Mockito.anyList());

		List<ModifyRequest> list = Lists.newArrayList(new ModifyRequest());

		LogFuture future = databaseOperate.onApply(
				Log.newBuilder().setData(ByteString.copyFrom(serializer.serialize(list)))
						.build());

		Assert.assertEquals(future.getError().getMessage(),
				"BadSqlGrammarException; bad SQL grammar [BadSqlGrammarException]; nested exception is java.sql.SQLException");
	}

	@Test
	public void test_logic_DataIntegrityViolationException() {

		Mockito.doThrow(
				new DataIntegrityViolationException("DataIntegrityViolationException"))
				.when(databaseOperate).onUpdate(Mockito.anyList());

		List<ModifyRequest> list = Lists.newArrayList(new ModifyRequest());

		LogFuture future = databaseOperate.onApply(
				Log.newBuilder().setData(ByteString.copyFrom(serializer.serialize(list)))
						.build());

		Assert.assertEquals(future.getError().getMessage(),
				"DataIntegrityViolationException");
	}

	@Test(expected = ConsistencyException.class)
	public void test_error_CannotGetJdbcConnectionException() {

		Mockito.doThrow(
				new CannotGetJdbcConnectionException("CannotGetJdbcConnectionException"))
				.when(databaseOperate).onUpdate(Mockito.anyList());

		List<ModifyRequest> list = Lists.newArrayList(new ModifyRequest());

		databaseOperate.onApply(
				Log.newBuilder().setData(ByteString.copyFrom(serializer.serialize(list)))
						.build());
	}

	@Test(expected = ConsistencyException.class)
	public void test_error_DataAccessException() {

		Mockito.doThrow(
				new DeadlockLoserDataAccessException("DeadlockLoserDataAccessException",
						new SQLException())).when(databaseOperate)
				.onUpdate(Mockito.anyList());

		List<ModifyRequest> list = Lists.newArrayList(new ModifyRequest());

		databaseOperate.onApply(
				Log.newBuilder().setData(ByteString.copyFrom(serializer.serialize(list)))
						.build());
	}

}
