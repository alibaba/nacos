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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Common methods for exception.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class ExceptionUtil {
    
    /**
     * Represents an empty exception, that is, no exception occurs, only a constant.
     */
    public static final Exception NONE_EXCEPTION = new RuntimeException("");
    
    public static String getAllExceptionMsg(Throwable e) {
        Throwable cause = e;
        StringBuilder strBuilder = new StringBuilder();
        
        while (cause != null && !StringUtils.isEmpty(cause.getMessage())) {
            strBuilder.append("caused: ").append(cause.getMessage()).append(";");
            cause = cause.getCause();
        }
        
        return strBuilder.toString();
    }
    
    public static Throwable getCause(final Throwable t) {
        final Throwable cause = t.getCause();
        if (Objects.isNull(cause)) {
            return t;
        }
        return cause;
    }
    
    public static String getStackTrace(final Throwable t) {
        if (t == null) {
            return "";
        }
        
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(out);
        t.printStackTrace(ps);
        ps.flush();
        return out.toString();
    }
}
