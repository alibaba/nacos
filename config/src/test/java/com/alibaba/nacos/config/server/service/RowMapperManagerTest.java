package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.service.repository.RowMapperManager;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.config.server.model.User;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.jdbc.core.RowMapper;

public class RowMapperManagerTest {

	@Test
	public void test_user_mapper() {
		RowMapper<User> mapper = new RowMapperManager.UserRowMapper();
		Assert.assertEquals(ClassUtils.resolveGenericTypeByInterface(mapper.getClass()).getSimpleName(), User.class.getSimpleName());
	}

}