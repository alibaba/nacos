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