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

package com.alibaba.nacos.common.executor;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NameThreadFactory implements ThreadFactory {

	private final AtomicInteger id = new AtomicInteger(0);

	private String name;

	public NameThreadFactory(String name) {
		if (!name.endsWith(StringUtils.DOT)) {
			name += StringUtils.DOT;
		}
		this.name = name;
	}

	@Override
    public Thread newThread(Runnable r) {
		String threadName = name + id.getAndDecrement();
		Thread thread = new Thread(r, threadName);
		thread.setDaemon(true);
		return thread;
	}
}
