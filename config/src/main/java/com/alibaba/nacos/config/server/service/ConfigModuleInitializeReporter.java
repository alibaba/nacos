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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.Config;
import com.alibaba.nacos.core.code.ModuleInitializeReporter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configure the initialization completion debrief for the module
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class ConfigModuleInitializeReporter implements ModuleInitializeReporter {

	private Map<String, Boolean> successRecord = new ConcurrentHashMap<>(4);
	private Map<String, Throwable> exceptionRecord = new ConcurrentHashMap<>(4);

	public void setSuccess(String name, boolean success) {
		successRecord.put(name, success);
	}

	public void setEx(String name, Throwable ex) {
		exceptionRecord.put(name, ex);
	}

	@Override
	public boolean alreadyInitialized() {
		boolean[] initialize = new boolean[] { !successRecord.isEmpty() };
		successRecord.forEach((group, isOk) -> initialize[0] &= isOk);
		return initialize[0] && exceptionRecord.isEmpty();
	}

	@Override
	public boolean hasException() {
		return !exceptionRecord.isEmpty();
	}

	@Override
	public Throwable getError() {
		StringBuilder errorBuilder = new StringBuilder();
		exceptionRecord.forEach((s, throwable) -> errorBuilder.append("[").append(s).append("]")
				.append(":").append(throwable.toString())
				.append(StringUtils.LF));
		return new NacosException(NacosException.SERVER_ERROR, errorBuilder.toString());
	}

	@Override
	public String group() {
		return Config.class.getName();
	}
}
