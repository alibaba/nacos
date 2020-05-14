/*
 *
 *  * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.alibaba.nacos.consistency.utils;

import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.google.protobuf.Message;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ProtobufUtils {

	public static Message parse(byte[] bytes) {
		Message result = null;
		try {
			result = Log.parseFrom(bytes);
			return result;
		} catch (Throwable ignore) { }

		try {
			result = GetRequest.parseFrom(bytes);
			return result;
		} catch (Throwable ignore) {}

		throw new ConsistencyException("The current array cannot be serialized to the corresponding object");
	}

}
