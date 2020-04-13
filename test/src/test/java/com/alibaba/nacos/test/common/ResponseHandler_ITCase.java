package com.alibaba.nacos.test.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.core.utils.GenericType;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ResponseHandler_ITCase {

	private ArrayList<Integer> list = new ArrayList<Integer>(Arrays.asList(1, 2, 3, 4, 5, 6));

	@Test
	public void test_deserialization_type() {
		String json = JSON.toJSONString(list);
		ArrayList<Integer> tmp = ResponseHandler.convert(json, new GenericType<List<Integer>>(){}.getType());
		Assert.assertEquals(list, tmp);
	}

	@Test
	public void test_rest_result() {
		String json = "{\"code\":200,\"message\":null,\"data\":[{\"USERNAME\":\"nacos\",\"PASSWORD\":\"$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu\",\"ENABLED\":true}]}";
		RestResult<Object> result = ResponseHandler.convert(json, new GenericType<RestResult<Object>>(){}.getType());
		System.out.println(result);
	}

	@Test
	public void test_deserialization_class() {
		String json = JSON.toJSONString(list);
		ArrayList<Integer> tmp = ResponseHandler.convert(json, ClassUtils.resolveGenericType(new GenericType<List<Integer>>(){}.getClass()));
		Assert.assertEquals(list, tmp);
	}

}