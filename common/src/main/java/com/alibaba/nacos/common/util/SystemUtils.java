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

package com.alibaba.nacos.common.util;

import com.sun.management.OperatingSystemMXBean;
import org.apache.commons.lang3.StringUtils;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nacos
 */
public class SystemUtils {

    /**
     * The System property name of  Standalone mode
     */
    public static final String STANDALONE_MODE_PROPERTY_NAME = "nacos.standalone";

    /**
     * Standalone mode or not
     */
    public static final boolean STANDALONE_MODE = Boolean.getBoolean(STANDALONE_MODE_PROPERTY_NAME);

    private static OperatingSystemMXBean operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public static List<String> getIPsBySystemEnv(String key) {
        String env = getSystemEnv(key);
        List<String> ips = new ArrayList<String>();
        if (StringUtils.isNotEmpty(env)) {
            ips = Arrays.asList(env.split(","));
        }
        return ips;
    }

    public static String getSystemEnv(String key) {
        String env = System.getenv(key);
        return env;
    }

    public static float getLoad() {
        return (float) operatingSystemMXBean.getSystemLoadAverage();
    }

    public static float getCPU() {
        return (float) operatingSystemMXBean.getSystemCpuLoad();
    }

    public static float getMem() {
        return (float) (1 - (double) operatingSystemMXBean.getFreePhysicalMemorySize() / (double) operatingSystemMXBean.getTotalPhysicalMemorySize());
    }
}
