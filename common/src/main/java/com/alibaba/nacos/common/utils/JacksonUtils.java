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

package com.alibaba.nacos.common.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class JacksonUtils {

	static ObjectMapper mapper = new ObjectMapper();

	static {
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	}

	public static String toJson(Object obj) throws Exception {
		return mapper.writeValueAsString(obj);
	}

	public static byte[] toJsonBytes(Object obj) throws Exception {
		return ByteUtils.toBytes(mapper.writeValueAsString(obj));
	}

	public static <T> T toObj(byte[] json, Class<T> cls) throws Exception {
		return toObj(StringUtils.newString4UTF8(json), cls);
	}

	public static <T> T toObj(byte[] json, Type cls) throws Exception {
		return toObj(StringUtils.newString4UTF8(json), cls);
	}

	public static <T> T toObj(String json, Class<T> cls) throws Exception {
		return mapper.readValue(json, cls);
	}

	public static <T> T toObj(String json, Type type) throws Exception {
		return mapper.readValue(json, mapper.constructType(type));
	}
}
