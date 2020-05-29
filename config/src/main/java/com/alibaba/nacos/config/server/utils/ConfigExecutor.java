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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.config.server.Config;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ConfigExecutor {

	private static final Executor DUMP_EXECUTOR = ExecutorFactory.newFixExecutorService(
			Config.class.getCanonicalName(),
			1,
			new NameThreadFactory("nacos.config.embedded.dump"));

	private static ScheduledExecutorService TIMER_EXECUTOR = ExecutorFactory.newScheduledExecutorService(Config.class.getCanonicalName(),
			10,
			new NameThreadFactory("com.alibaba.nacos.server.Timer"));

	static public void scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
			TimeUnit unit) {
		TIMER_EXECUTOR.scheduleWithFixedDelay(command, initialDelay, delay, unit);
	}

	public static void executeEmbeddedDump(Runnable runnable) {
		DUMP_EXECUTOR.execute(runnable);
	}

}
