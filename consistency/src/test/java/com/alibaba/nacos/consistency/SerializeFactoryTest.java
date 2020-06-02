package com.alibaba.nacos.consistency;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class SerializeFactoryTest {


	@Test
	public void test_list_serialize() throws Exception {
		byte[] data = new byte[0];
		Serializer serializer = SerializeFactory.getDefault();

		List<Integer> logsList = new ArrayList<>();
		for (int i = 0; i < 4; i ++) {
			logsList.add(i);
		}

		data = serializer.serialize(logsList);
		Assert.assertNotEquals(0, data.length);

		ArrayList<Integer> list = serializer.deserialize(data, ArrayList.class);
		System.out.println(list);
	}

	@Test
	public void test_map_serialize() {
		byte[] data = new byte[0];
		Serializer serializer = SerializeFactory.getDefault();
		Map<Integer, Integer> logsMap = new HashMap<>();
		for (int i = 0; i < 4; i ++) {
			logsMap.put(i, i);
		}
		data = serializer.serialize(logsMap);
		Assert.assertNotEquals(0, data.length);
		Map<Integer, Integer> result = serializer.deserialize(data, HashMap.class);
		System.out.println(result);
	}

	@Test
	public void test_set_serialize() {
		byte[] data = new byte[0];
		Serializer serializer = SerializeFactory.getDefault();
		Set<Integer> logsMap = new CopyOnWriteArraySet<>();
		for (int i = 0; i < 4; i ++) {
			logsMap.add(i);
		}

		data = serializer.serialize(logsMap);
		Assert.assertNotEquals(0, data.length);
		Set<Integer> result = serializer.deserialize(data, HashSet.class);
		System.out.println(result);
	}

}