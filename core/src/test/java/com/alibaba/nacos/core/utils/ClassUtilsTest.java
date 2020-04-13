package com.alibaba.nacos.core.utils;

import com.google.common.reflect.TypeToken;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ClassUtilsTest {

	@Test
	public void test_generic() {
		GenericType<List<String>> genericType = new GenericType<List<String>>(){};
		Assert.assertEquals(genericType.getType(), new TypeToken<java.util.List<java.lang.String>>(){}.getType());
	}

}