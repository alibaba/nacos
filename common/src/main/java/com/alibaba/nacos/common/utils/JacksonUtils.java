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

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class JacksonUtils {

	static ObjectMapper mapper = new ObjectMapper();

	static {
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.setSerializationInclusion(Include.NON_NULL);
	}

	public static String toJson(Object obj) {
		try {
			return mapper.writeValueAsString(obj);
		}
		catch (JsonProcessingException e) {
			throw new NacosSerializationException(obj.getClass(), e);
		}
	}

	public static byte[] toJsonBytes(Object obj) {
		try {
			return ByteUtils.toBytes(mapper.writeValueAsString(obj));
		}
		catch (JsonProcessingException e) {
			throw new NacosSerializationException(obj.getClass(), e);
		}
	}

	public static <T> T toObj(byte[] json, Class<T> cls) {
		try {
			return toObj(StringUtils.newString4UTF8(json), cls);
		}
		catch (Exception e) {
			throw new NacosDeserializationException(cls, e);
		}
	}

	public static <T> T toObj(byte[] json, Type cls) {
		try {
			return toObj(StringUtils.newString4UTF8(json), cls);
		}
		catch (Exception e) {
			throw new NacosDeserializationException(e);
		}
	}

	public static <T> T toObj(byte[] json, TypeReference<T> typeReference) {
		try {
			return toObj(StringUtils.newString4UTF8(json), typeReference);
		}
		catch (Exception e) {
			throw new NacosDeserializationException(e);
		}
	}

	public static <T> T toObj(String json, Class<T> cls) {
		try {
			return mapper.readValue(json, cls);
		}
		catch (IOException e) {
			throw new NacosDeserializationException(cls, e);
		}
	}

	public static <T> T toObj(String json, Type type) {
		try {
			return mapper.readValue(json, mapper.constructType(type));
		}
		catch (IOException e) {
			throw new NacosDeserializationException(e);
		}
	}

	public static <T> T toObj(String json, TypeReference<T> typeReference) {
		try {
			return mapper.readValue(json, typeReference);
		}
		catch (IOException e) {
			throw new NacosDeserializationException(typeReference.getClass(), e);
		}
	}

	public static JsonNode toObj(String json) {
		try {
			return mapper.readTree(json);
		}
		catch (IOException e) {
			throw new NacosDeserializationException(e);
		}
	}

	public static void registerSubtype(Class<?> clz, String type) {
		mapper.registerSubtypes(new NamedType(clz, type));
	}

	public static ObjectNode createEmptyJsonNode() {
		return new ObjectNode(mapper.getNodeFactory());
	}

	public static ArrayNode createEmptyArrayNode() {
		return new ArrayNode(mapper.getNodeFactory());
	}

	public static JsonNode transferToJsonNode(Object obj) {
		return mapper.valueToTree(obj);
	}
}
