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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.common.utils.IoUtils;
import com.sun.management.OperatingSystemMXBean;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.alibaba.nacos.core.utils.Constants.FUNCTION_MODE_PROPERTY_NAME;
import static com.alibaba.nacos.core.utils.Constants.STANDALONE_MODE_PROPERTY_NAME;
import static org.apache.commons.lang3.CharEncoding.UTF_8;

/**
 * @author nacos
 */
public class SystemUtils {

    /**
     * Standalone mode or not
     */
    public static final boolean STANDALONE_MODE = Boolean.getBoolean(STANDALONE_MODE_PROPERTY_NAME);

    public static final String STANDALONE_MODE_ALONE = "standalone";
    public static final String STANDALONE_MODE_CLUSTER = "cluster";

    /**
     * server
     */
    public static final String FUNCTION_MODE = System.getProperty(FUNCTION_MODE_PROPERTY_NAME);

    public static final String FUNCTION_MODE_CONFIG = "config";
    public static final String FUNCTION_MODE_NAMING = "naming";


    private static OperatingSystemMXBean operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory
        .getOperatingSystemMXBean();

    /**
     * nacos local ip
     */
    public static final String LOCAL_IP = InetUtils.getSelfIp();


    /**
     * The key of nacos home.
     */
    public static final String NACOS_HOME_KEY = "nacos.home";

    /**
     * The home of nacos.
     */
    public static final String NACOS_HOME = getNacosHome();

    /**
     * The file path of cluster conf.
     */
    public static final String CLUSTER_CONF_FILE_PATH = getClusterConfFilePath();

    public static List<String> getIPsBySystemEnv(String key) {
        String env = getSystemEnv(key);
        List<String> ips = new ArrayList<>();
        if (StringUtils.isNotEmpty(env)) {
            ips = Arrays.asList(env.split(","));
        }
        return ips;
    }

    public static String getSystemEnv(String key) {
        return System.getenv(key);
    }

    public static float getLoad() {
        return (float) operatingSystemMXBean.getSystemLoadAverage();
    }

    public static float getCPU() {
        return (float) operatingSystemMXBean.getSystemCpuLoad();
    }

    public static float getMem() {
        return (float) (1 - (double) operatingSystemMXBean.getFreePhysicalMemorySize() / (double) operatingSystemMXBean
            .getTotalPhysicalMemorySize());
    }

    private static String getNacosHome() {
        String nacosHome = System.getProperty(NACOS_HOME_KEY);
        if (StringUtils.isBlank(nacosHome)) {
            nacosHome = System.getProperty("user.home") + File.separator + "nacos";
        }
        return nacosHome;
    }

    public static String getConfFilePath() {
        return NACOS_HOME + File.separator + "conf" + File.separator;
    }

    private static String getClusterConfFilePath() {
        return NACOS_HOME + File.separator + "conf" + File.separator + "cluster.conf";
    }

    public static List<String> readClusterConf() throws IOException {
        List<String> instanceList = new ArrayList<String>();
        try(Reader reader = new InputStreamReader(new FileInputStream(new File(CLUSTER_CONF_FILE_PATH)),
        StandardCharsets.UTF_8)) {
            List<String> lines = IoUtils.readLines(reader);
            String comment = "#";
            for (String line : lines) {
                String instance = line.trim();
                if (instance.startsWith(comment)) {
                    // # it is ip
                    continue;
                }
                if (instance.contains(comment)) {
                    // 192.168.71.52:8848 # Instance A
                    instance = instance.substring(0, instance.indexOf(comment));
                    instance = instance.trim();
                }
                int multiIndex = instance.indexOf(Constants.COMMA_DIVISION);
                if (multiIndex > 0) {
                    // support the format: ip1:port,ip2:port  # multi inline
                    instanceList.addAll(Arrays.asList(instance.split(Constants.COMMA_DIVISION)));
                } else {
                    //support the format: 192.168.71.52:8848
                    instanceList.add(instance);
                }
            }
            return instanceList;
        }
    }

    public static void writeClusterConf(String content) throws IOException {
        IoUtils.writeStringToFile(new File(CLUSTER_CONF_FILE_PATH), content, UTF_8);
    }

}
