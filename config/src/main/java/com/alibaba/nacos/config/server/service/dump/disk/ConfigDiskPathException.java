/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.dump.disk;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;

/**
 * Exception thrown when a Config identity cannot be resolved to its intended disk target.
 *
 * @author Nacos
 */
public class ConfigDiskPathException extends NacosRuntimeException {
    
    private static final long serialVersionUID = -1547220833146395939L;
    
    private static final int MAX_LOG_VALUE_LENGTH = 256;
    
    /**
     * Create an unsafe Config disk path exception.
     *
     * @param parameterName rejected parameter name
     * @param parameterValue rejected parameter value
     */
    public ConfigDiskPathException(String parameterName, String parameterValue) {
        super(NacosException.INVALID_PARAM, buildMessage(parameterName, parameterValue));
    }
    
    /**
     * Create an unsafe Config disk path exception with its validation cause.
     *
     * @param parameterName rejected parameter name
     * @param parameterValue rejected parameter value
     * @param cause validation cause
     */
    public ConfigDiskPathException(String parameterName, String parameterValue, Throwable cause) {
        super(NacosException.INVALID_PARAM, buildMessage(parameterName, parameterValue), cause);
    }
    
    private static String buildMessage(String parameterName, String parameterValue) {
        return String.format("Rejected unsafe Config disk path parameter: %s='%s'", parameterName,
            sanitizeForLog(parameterValue));
    }
    
    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "<null>";
        }
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_LOG_VALUE_LENGTH));
        int limit = Math.min(value.length(), MAX_LOG_VALUE_LENGTH);
        for (int i = 0; i < limit; i++) {
            char ch = value.charAt(i);
            int characterType = Character.getType(ch);
            if (Character.isISOControl(ch) || characterType == Character.FORMAT
                || characterType == Character.LINE_SEPARATOR
                || characterType == Character.PARAGRAPH_SEPARATOR) {
                result.append('?');
            } else if (ch == '\\' || ch == '\'') {
                result.append('\\').append(ch);
            } else {
                result.append(ch);
            }
        }
        if (value.length() > MAX_LOG_VALUE_LENGTH) {
            result.append("...");
        }
        return result.toString();
    }
}
