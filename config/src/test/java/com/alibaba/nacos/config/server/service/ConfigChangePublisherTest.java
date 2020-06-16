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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigChangePublisherTest {

	@Test
	public void testConfigChangeNotify() {

		AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();

		EventDispatcher.addEventListener(new EventDispatcher.AbstractEventListener() {
			@Override
			public List<Class<? extends EventDispatcher.Event>> interest() {
				return Collections.singletonList(ConfigDataChangeEvent.class);
			}

			@Override
			public void onEvent(EventDispatcher.Event event) {
				reference.set((ConfigDataChangeEvent) event);
			}
		});

		// nacos is standalone mode and use embedded storage
		ApplicationUtils.setIsStandalone(true);
		PropertyUtil.setEmbeddedStorage(true);

		ConfigChangePublisher.notifyConfigChange(new ConfigDataChangeEvent("chuntaojun", "chuntaojun", System.currentTimeMillis()));
		Assert.assertNotNull(reference.get());
		reference.set(null);


		// nacos is standalone mode and use external storage
		ApplicationUtils.setIsStandalone(true);
		PropertyUtil.setEmbeddedStorage(false);

		ConfigChangePublisher.notifyConfigChange(new ConfigDataChangeEvent("chuntaojun", "chuntaojun", System.currentTimeMillis()));
		Assert.assertNotNull(reference.get());
		reference.set(null);


		// nacos is cluster mode and use embedded storage
		ApplicationUtils.setIsStandalone(false);
		PropertyUtil.setEmbeddedStorage(true);

		ConfigChangePublisher.notifyConfigChange(new ConfigDataChangeEvent("chuntaojun", "chuntaojun", System.currentTimeMillis()));
		Assert.assertNull(reference.get());
		reference.set(null);


		// nacos is cluster mode and use external storage
		ApplicationUtils.setIsStandalone(false);
		PropertyUtil.setEmbeddedStorage(false);

		ConfigChangePublisher.notifyConfigChange(new ConfigDataChangeEvent("chuntaojun", "chuntaojun", System.currentTimeMillis()));
		Assert.assertNotNull(reference.get());
		reference.set(null);
	}

}
