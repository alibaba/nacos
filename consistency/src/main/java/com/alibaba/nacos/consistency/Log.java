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

package com.alibaba.nacos.consistency;

import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Log implements Serializable {

	private static final long serialVersionUID = 5151021912611953768L;
	protected String group;
	protected String key;
	protected Object data;
	protected String className;
	protected String operation;
	protected Map<String, String> extendInfo = new HashMap<>(4);

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public <T> T getData() {
		return (T) data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getOperation() {
		return operation;
	}

	public void setOperation(String operation) {
		this.operation = operation;
	}

	public Map<String, String> getExtendInfo() {
		return extendInfo;
	}

	public void setExtendInfo(Map<String, String> extendInfo) {
		this.extendInfo = extendInfo;
	}

	public Log putExtendInfoByKey(String key, String value) {
		this.extendInfo.put(key, value);
		return this;
	}

	public String getExtendInfoOrDefault(String key, String defaultVal) {
		final String result = extendInfo.get(key);
		if (StringUtils.isEmpty(result)) {
			return defaultVal;
		}
		return result;
	}

	public static LogBuilder newBuilder() {
		return new LogBuilder();
	}

	public static final class LogBuilder {
		protected String group;
		protected String key;
		protected Object data;
		protected String className;
		protected String operation;
		protected Map<String, String> extendInfo = new HashMap<>(4);

		private LogBuilder() {
		}

		public LogBuilder group(String group) {
			this.group = group;
			return this;
		}

		public LogBuilder key(String key) {
			this.key = key;
			return this;
		}

		public LogBuilder data(Object data) {
			this.data = data;
			return this;
		}

		public LogBuilder className(String className) {
			this.className = className;
			return this;
		}

		public LogBuilder operation(String operation) {
			this.operation = operation;
			return this;
		}

		public LogBuilder extendInfo(Map<String, String> extendInfo) {
			this.extendInfo.putAll(extendInfo);
			return this;
		}

		public LogBuilder addExtendInfo(String key, String value) {
			this.extendInfo.put(key, value);
			return this;
		}

		public Log build() {
			Log log = new Log();
			log.setGroup(group);
			log.setKey(key);
			log.setData(data);
			log.setClassName(className);
			log.setOperation(operation);
			log.setExtendInfo(extendInfo);
			return log;
		}
	}
}
