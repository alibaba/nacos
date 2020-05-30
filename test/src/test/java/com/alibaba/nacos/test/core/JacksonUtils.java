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

package com.alibaba.nacos.test.core;/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class JacksonUtils {

	static ObjectMapper mapper = new ObjectMapper();

	static {
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	}

	public static String serializeObject(Object o) throws IOException {
		return mapper.writeValueAsString(o);
	}

	public static Object deserializeObject(String s, Class<?> clazz) throws IOException {
		return mapper.readValue(s, clazz);
	}

	public static <T> T deserializeObject(String s, TypeReference<T> typeReference)
			throws IOException {
		return mapper.readValue(s, typeReference);
	}

	public static <T> T deserializeObject(InputStream src, TypeReference<?> typeReference)
			throws IOException {
		return mapper.readValue(src, typeReference);
	}

	@Test
	public void test_print() throws Exception {
		Map<String, Map<String, Object>> map = new HashMap<>();
		Map<String, Object> data = new HashMap<>();
		data.put("test-1", LocalDateTime.now());
		data.put("test_2", LocalDateTime.now());
		map.put("global", data);

		ProtocolMetaData metaData = new ProtocolMetaData();

		metaData.load(map);

		String json = serializeObject(metaData);
		System.out.println(json);
	}

}
