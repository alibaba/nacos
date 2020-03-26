package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.common.utils.ClassUtils;
import com.alibaba.nacos.config.server.model.User;
import org.junit.Test;
import org.springframework.jdbc.core.RowMapper;

public class RowMapperManagerTest {

	@Test
	public void test_user_mapper() {
		RowMapper<User> mapper = new RowMapperManager.UserRowMapper();
		System.out.println(ClassUtils.resolveGenericTypeByInterface(mapper.getClass()));
	}

}