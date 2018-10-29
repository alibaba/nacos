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
package com.alibaba.nacos.api.config;

import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * Config Interface
 * 
 * @author Nacos
 *
 */
public interface ConfigService {

	/**
	 * Get Configuration
	 * 
	 * @param dataId
	 *            Config ID
	 * @param group
	 *            Config Group
	 * @param timeoutMs
	 *            read timeout
	 * @return config value
	 * @throws NacosException
	 *             NacosException
	 */
	public String getConfig(String dataId, String group, long timeoutMs) throws NacosException;

	/**
	 * Add a listener to the configuration, after the server to modify the
	 * configuration, the client will use the incoming listener callback.
	 * Recommended asynchronous processing, the application can implement the
	 * getExecutor method in the ManagerListener, provide a thread pool of
	 * execution. If provided, use the main thread callback, May block other
	 * configurations or be blocked by other configurations.
	 * 
	 * @param dataId
	 *            Config ID
	 * @param group
	 *            Config Group
	 * @param listener
	 *            listener
	 * @throws NacosException
	 *             NacosException
	 */
	public void addListener(String dataId, String group, Listener listener) throws NacosException;

	/**
	 * publish config.
	 * 
	 * @param dataId
	 *            Config ID
	 * @param group
	 *            Config Group
	 * @param content
	 *            Config Content
	 * @return Whether publish
	 * @throws NacosException
	 *             NacosException
	 */
	public boolean publishConfig(String dataId, String group, String content) throws NacosException;

	/**
	 * Remove Config
	 * 
	 * @param dataId
	 *            Config ID
	 * @param group
	 *            Config Group
	 * @return whether remove
	 * @throws NacosException
	 *             NacosException
	 */
	public boolean removeConfig(String dataId, String group) throws NacosException;

	/**
	 * Remove Listener
	 * 
	 * @param dataId
	 *            Config ID
	 * @param group
	 *            Config Group
	 * @param listener
	 *            listener
	 */
	public void removeListener(String dataId, String group, Listener listener);
	
	/**
	 * server health info
	 * 
	 * @return whether health
	 */
	public String getServerStatus();

}
