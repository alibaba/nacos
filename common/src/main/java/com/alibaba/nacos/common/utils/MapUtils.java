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

package com.alibaba.nacos.common.utils;

import java.util.Collection;
import java.util.Dictionary;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MapUtils {

	public static void putIfValNoNull(Map target, Object key, Object value) {
		Objects.requireNonNull(key, "key");
		if (value != null) {
			target.put(key, value);
		}
	}

	public static void putIfValNoEmpty(Map target, Object key, Object value) {
		Objects.requireNonNull(key, "key");
		if (value instanceof String) {
			if (StringUtils.isNotBlank((String) value)) {
				target.put(key, value);
			}
		}
		if (value instanceof Collection) {
			if (CollectionUtils.isNotEmpty((Collection) value)) {
				target.put(key, value);
			}
		}
		if (value instanceof Dictionary) {
			if (CollectionUtils.isNotEmpty((Dictionary) value)) {
				target.put(key, value);
			}
		}

		// If you cannot determine if it is empty, null is returned
		putIfValNoNull(target, key, value);
	}

}
