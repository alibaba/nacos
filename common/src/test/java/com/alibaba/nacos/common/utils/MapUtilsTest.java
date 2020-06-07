package com.alibaba.nacos.common.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapUtilsTest {

	@Test
	public void test_map() {
		Map<Object, Object> map = new HashMap<Object, Object>();

		MapUtils.putIfValNoNull(map, "key-1", null);
		Assert.assertFalse(map.containsKey("key-1"));

		MapUtils.putIfValNoEmpty(map, "key-str", null);
		Assert.assertFalse(map.containsKey("key-str"));

		MapUtils.putIfValNoEmpty(map, "key-str", "");
		Assert.assertFalse(map.containsKey("key-str"));

		MapUtils.putIfValNoEmpty(map, "key-str", "1");
		Assert.assertTrue(map.containsKey("key-str"));

		MapUtils.putIfValNoEmpty(map, "key-list", null);
		Assert.assertFalse(map.containsKey("key-list"));

		MapUtils.putIfValNoEmpty(map, "key-list", Collections.emptyList());
		Assert.assertFalse(map.containsKey("key-list"));

		MapUtils.putIfValNoEmpty(map, "key-list", Collections.singletonList(1));
		Assert.assertTrue(map.containsKey("key-list"));

		MapUtils.putIfValNoEmpty(map, "key-map", null);
		Assert.assertFalse(map.containsKey("key-map"));

		MapUtils.putIfValNoEmpty(map, "key-map", Collections.emptyMap());
		Assert.assertFalse(map.containsKey("key-map"));

		Map<String, String> map1 = new HashMap<String, String>();
		map1.put("1123", "123");

		MapUtils.putIfValNoEmpty(map, "key-map", map1);
		Assert.assertTrue(map.containsKey("key-map"));
	}

}