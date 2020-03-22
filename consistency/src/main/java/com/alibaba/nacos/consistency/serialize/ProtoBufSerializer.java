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

package com.alibaba.nacos.consistency.serialize;

import com.alibaba.nacos.consistency.Serializer;
import io.protostuff.ByteArrayInput;
import io.protostuff.Input;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ProtoBufSerializer implements Serializer {

	private ThreadLocal<LinkedBuffer> bufferThreadLocal = ThreadLocal
			.withInitial(LinkedBuffer::allocate);

	@Override
	public <T> T deserialize(byte[] data, Class cls) {
		if (Map.class.isAssignableFrom(cls) || Collection.class
				.isAssignableFrom(cls)) {
			Schema<SerializeWrap> schema = RuntimeSchema
					.getSchema(SerializeWrap.class);
			SerializeWrap msg = schema.newMessage();
			Input input = new ByteArrayInput(data, 0, data.length, true);
			try {
				schema.mergeFrom(input, msg);
				return (T) msg.getData();
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		else {
			Schema<T> schema = RuntimeSchema.getSchema(cls);
			T msg = schema.newMessage();
			Input input = new ByteArrayInput(data, 0, data.length, true);
			try {
				schema.mergeFrom(input, msg);
				return msg;
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public <T> byte[] serialize(T obj) {
		Object waitSerialize = null;
		Class<T> cls = (Class<T>) obj.getClass();
		Schema schema = null;
		if (Map.class.isAssignableFrom(cls) || Collection.class
				.isAssignableFrom(cls)) {
			SerializeWrap<T> wrap = new SerializeWrap<>();
			wrap.setData(obj);
			schema = RuntimeSchema.getSchema(SerializeWrap.class);
			waitSerialize = wrap;
		}
		else {
			schema = RuntimeSchema.getSchema(cls);
			waitSerialize = obj;
		}

		LinkedBuffer buffer = bufferThreadLocal.get();
		try {
			return ProtostuffIOUtil.toByteArray(waitSerialize, schema, buffer);
		}
		finally {
			buffer.clear();
			bufferThreadLocal.set(buffer);
		}
	}

	@Override
	public String name() {
		return "protostuff";
	}

	private static class SerializeWrap<D> {

		private D data;

		public SerializeWrap() {
		}

		public D getData() {
			return data;
		}

		public void setData(D data) {
			this.data = data;
		}
	}
}
