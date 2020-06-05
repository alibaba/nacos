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
package com.alibaba.nacos.core.code;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.executor.ThreadPoolManager;
import com.alibaba.nacos.core.file.WatchFileCenter;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.core.utils.DiskUtils;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.context.event.EventPublishingRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Logging starting message {@link SpringApplicationRunListener} before {@link EventPublishingRunListener} execution
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.5.0
 */
public class StartingSpringApplicationRunListener
		implements SpringApplicationRunListener, Ordered {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(StartingSpringApplicationRunListener.class);

	private static final String MODE_PROPERTY_KEY_STAND_MODE = "nacos.mode";

	private static final String MODE_PROPERTY_KEY_FUNCTION_MODE = "nacos.function.mode";

	private static final String LOCAL_IP_PROPERTY_KEY = "nacos.local.ip";

	private ScheduledExecutorService scheduledExecutorService;

	private volatile boolean starting;

	public StartingSpringApplicationRunListener(SpringApplication application,
			String[] args) {

	}

	@Override
	public void starting() {
		starting = true;
	}

	@Override
	public void environmentPrepared(ConfigurableEnvironment environment) {
		ApplicationUtils.injectEnvironment(environment);
		if (ApplicationUtils.getStandaloneMode()) {
			System.setProperty(MODE_PROPERTY_KEY_STAND_MODE, "stand alone");
		}
		else {
			System.setProperty(MODE_PROPERTY_KEY_STAND_MODE, "cluster");
		}
		if (ApplicationUtils.getFunctionMode() == null) {
			System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, "All");
		}
		else if (ApplicationUtils.FUNCTION_MODE_CONFIG
				.equals(ApplicationUtils.getFunctionMode())) {
			System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE,
					ApplicationUtils.FUNCTION_MODE_CONFIG);
		}
		else if (ApplicationUtils.FUNCTION_MODE_NAMING
				.equals(ApplicationUtils.getFunctionMode())) {
			System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE,
					ApplicationUtils.FUNCTION_MODE_NAMING);
		}

		System.setProperty(LOCAL_IP_PROPERTY_KEY, InetUtils.getSelfIp());
	}

	@Override
	public void contextPrepared(ConfigurableApplicationContext context) {
		logClusterConf();
		logStarting();
	}

	@Override
	public void contextLoaded(ConfigurableApplicationContext context) {

	}

	@Override
	public void started(ConfigurableApplicationContext context) {
		starting = false;
		ConfigurableEnvironment env = context.getEnvironment();

		closeExecutor();

		logFilePath();

		// External data sources are used by default in cluster mode
		boolean useExternalStorage = ("mysql".equalsIgnoreCase(env.getProperty("spring.datasource.platform", "")));

		// must initialize after setUseExternalDB
		// This value is true in stand-alone mode and false in cluster mode
		// If this value is set to true in cluster mode, nacos's distributed storage engine is turned on
		// default value is depend on ${nacos.standalone}

		if (!useExternalStorage) {
			boolean embeddedStorage = ApplicationUtils.getStandaloneMode() || Boolean.getBoolean("embeddedStorage");
			// If the embedded data source storage is not turned on, it is automatically
			// upgraded to the external data source storage, as before
			if (!embeddedStorage) {
				useExternalStorage = true;
			}
		}

		LOGGER.info("Nacos started successfully in {} mode. use {} storage",
				System.getProperty(MODE_PROPERTY_KEY_STAND_MODE), useExternalStorage ? "external" : "embedded");
	}

	@Override
	public void running(ConfigurableApplicationContext context) {

	}

	@Override
	public void failed(ConfigurableApplicationContext context, Throwable exception) {
		starting = false;

		logFilePath();

		LOGGER.error("Startup errors : {}", exception);

		HttpClientManager.shutdown();
		ThreadPoolManager.shutdown();
		WatchFileCenter.shutdown();
		NotifyCenter.shutdown();

		closeExecutor();

		context.close();

		LOGGER.error("Nacos failed to start, please see {} for more details.",
				Paths.get(ApplicationUtils.getNacosHome(), "logs/nacos.log"));
	}

	/**
	 * Before {@link EventPublishingRunListener}
	 *
	 * @return HIGHEST_PRECEDENCE
	 */
	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE;
	}

	private void logClusterConf() {
		if (!ApplicationUtils.getStandaloneMode()) {
			try {
				List<String> clusterConf = ApplicationUtils.readClusterConf();
				LOGGER.info("The server IP list of Nacos is {}", clusterConf);
			}
			catch (IOException e) {
				LOGGER.error("read cluster conf fail", e);
			}
		}
	}

	private void logFilePath() {
		String[] dirNames = new String[] { "logs", "conf", "data" };
		for (String dirName : dirNames) {
			LOGGER.info("Nacos Log files: {}",
					Paths.get(ApplicationUtils.getNacosHome(), dirName).toString());
			try {
				DiskUtils.forceMkdir(new File(
						Paths.get(ApplicationUtils.getNacosHome(), dirName).toUri()));
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void closeExecutor() {
		if (scheduledExecutorService != null) {
			scheduledExecutorService.shutdownNow();
		}
	}

	private void logStarting() {
		if (!ApplicationUtils.getStandaloneMode()) {

			scheduledExecutorService = ExecutorFactory
					.newSingleScheduledExecutorService(getClass().getCanonicalName(),
							new NameThreadFactory("nacos-starting"));

			scheduledExecutorService.scheduleWithFixedDelay(() -> {
				if (starting) {
					LOGGER.info("Nacos is starting...");
				}
			}, 1, 1, TimeUnit.SECONDS);
		}
	}
}
