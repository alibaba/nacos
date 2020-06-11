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

package com.alibaba.nacos.common.utils;

import org.slf4j.Logger;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class LoggerUtils {

	public static final String TRACE = "TRACE";

	public static final String INFO = "INFO";

	public static final String DEBUG = "DEBUG";

	public static final String WARN = "WARN";

	public static final String ERROR = "ERROR";

	public static void printIfDebugEnabled(Logger logger, String s, Object... args) {
		if (logger.isDebugEnabled()) {
			logger.debug(s, args);
		}
	}

	public static void printIfInfoEnabled(Logger logger, String s, Object... args) {
		if (logger.isInfoEnabled()) {
			logger.info(s, args);
		}
	}

	public static void printIfTraceEnabled(Logger logger, String s, Object... args) {
		if (logger.isTraceEnabled()) {
			logger.trace(s, args);
		}
	}

	public static void printIfWarnEnabled(Logger logger, String s, Object... args) {
		if (logger.isWarnEnabled()) {
			logger.warn(s, args);
		}
	}

	public static void printIfErrorEnabled(Logger logger, String s, Object... args) {
		if (logger.isErrorEnabled()) {
			logger.error(s, args);
		}
	}

}
