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
/*
 * Copyright 2001-2004 The Apache Software Foundation.
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

package com.alibaba.nacos.client.logger.support;

import java.io.PrintStream;
import java.util.Calendar;

/**
 * logger log
 *
 * @author Nacos
 */
public class LogLog {

    private static final String CLASS_INFO = LogLog.class.getClassLoader().toString();

    private static boolean debugEnabled = false;
    private static boolean infoEnabled = true;

    private static boolean quietMode = false;

    private static final String DEBUG_PREFIX = "JM.Log:DEBUG ";
    private static final String INFO_PREFIX = "JM.Log:INFO ";

    private static final String WARN_PREFIX = "JM.Log:WARN ";
    private static final String ERR_PREFIX = "JM.Log:ERROR ";

    public static void setQuietMode(boolean quietMode) {
        LogLog.quietMode = quietMode;
    }

    static public void setInternalDebugging(boolean enabled) {
        debugEnabled = enabled;
    }

    static public void setInternalInfoing(boolean enabled) {
        infoEnabled = enabled;
    }

    public static void debug(String msg) {
        if (debugEnabled && !quietMode) {
            println(System.out, DEBUG_PREFIX + msg);
        }
    }

    public static void debug(String msg, Throwable t) {
        if (debugEnabled && !quietMode) {
            println(System.out, DEBUG_PREFIX + msg);
            if (t != null) {
                t.printStackTrace(System.out);
            }
        }
    }

    public static void info(String msg) {
        if (infoEnabled && !quietMode) {
            println(System.out, INFO_PREFIX + msg);
        }
    }

    public static void info(String msg, Throwable t) {
        if (infoEnabled && !quietMode) {
            println(System.out, INFO_PREFIX + msg);
            if (t != null) {
                t.printStackTrace(System.out);
            }
        }
    }

    public static void error(String msg) {
        if (quietMode) {
            return;
        }

        println(System.err, ERR_PREFIX + msg);
    }

    public static void error(String msg, Throwable t) {
        if (quietMode) {
            return;
        }

        println(System.err, ERR_PREFIX + msg);
        if (t != null) {
            t.printStackTrace();
        }
    }

    public static void warn(String msg) {
        if (quietMode) {
            return;
        }

        println(System.err, WARN_PREFIX + msg);
    }

    public static void warn(String msg, Throwable t) {
        if (quietMode) {
            return;
        }

        println(System.err, WARN_PREFIX + msg);
        if (t != null) {
            t.printStackTrace();
        }
    }

    private static void println(PrintStream out, String msg) {
        out.println(Calendar.getInstance().getTime().toString() + " " + CLASS_INFO + " " + msg);
    }

    private static void outPrintln(PrintStream out, String msg) {
        out.println(Calendar.getInstance().getTime().toString() + " " + CLASS_INFO + " " + msg);
    }
}
