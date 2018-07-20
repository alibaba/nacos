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
package com.alibaba.nacos.api;

import java.util.Properties;

import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;

/**
 * Nacos Factory
 * 
 * @author Nacos
 *
 */
public class NacosFactory {

	/**
	 * Create config
	 * 
	 * @param properties
	 *            init param
	 * @return config
	 * @throws NacosException
	 *             Exception
	 */
	public static ConfigService createConfigService(Properties properties) throws NacosException {
		return ConfigFactory.createConfigService(properties);
	}

	/**
	 * Create config
	 * 
	 * @param serverAddr
	 *            server list
	 * @return config
	 * @throws NacosException
	 *             Exception
	 */
	public static ConfigService createConfigService(String serverAddr) throws NacosException {
		return ConfigFactory.createConfigService(serverAddr);
	}

	/**
	 * Create Naming
	 * 
	 * @param serverAddr
	 *            server list
	 * @return Naming
	 * @throws NacosException
	 *             Exception
	 */
	public static NamingService createNamingService(String serverAddr) throws NacosException {
		return NamingFactory.createNamingService(serverAddr);
	}

	/**
	 * Create Naming
	 * 
	 * @param properties
	 *            init param
	 * @return Naming
	 * @throws NacosException
	 *             Exception
	 */
	public static NamingService createNamingService(Properties properties) throws NacosException {
		return NamingFactory.createNamingService(properties);
	}

}
