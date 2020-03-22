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
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class KryoSerializer  implements Serializer {

	private final KryoPool kryoPool;

	public KryoSerializer() {
		kryoPool = new KryoPool.Builder(new KryoFactory()).softReferences().build();
	}

	@Override
	public <T> T deserialize(byte[] data, Class cls) {
		return kryoPool.run(kryo -> {
			ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
			com.esotericsoftware.kryo.io.Input input = new com.esotericsoftware.kryo.io.Input(byteArrayInputStream);
			return (T) kryo.readClassAndObject(input);
		});
	}

	@Override
	public <T> byte[] serialize(T obj) {
		return kryoPool.run(kryo -> {
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			Output output = new Output(byteArrayOutputStream);
			kryo.writeClassAndObject(output, obj);
			output.close();
			return byteArrayOutputStream.toByteArray();
		});
	}

	@Override
	public String name() {
		return "Kryo";
	}

	private static class KryoFactory implements com.esotericsoftware.kryo.pool.KryoFactory {

		@Override
		public Kryo create() {
			Kryo kryo = new Kryo();
			kryo.setRegistrationRequired(false);
			kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(
					new org.objenesis.strategy.StdInstantiatorStrategy()));
			return kryo;
		}

	}
}