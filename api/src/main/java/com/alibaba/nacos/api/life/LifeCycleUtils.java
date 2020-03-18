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
package com.alibaba.nacos.api.life;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class LifeCycleUtils {

	public static void invokeInit(LifeCycle lifeCycle) throws Exception {
		if (lifeCycle != null) {
			lifeCycle.init();
		}
	}

	public static void invokeDestroy(LifeCycle lifeCycle) throws Exception {
		if (lifeCycle != null) {
			lifeCycle.destroy();
		}
	}

	public static void registerShutdownHook(final LifeCycle lifeCycle) {
		if (lifeCycle != null) {
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						invokeDestroy(lifeCycle);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}));
		}
	}

}
