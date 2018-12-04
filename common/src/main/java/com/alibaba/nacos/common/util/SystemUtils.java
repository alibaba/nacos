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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import static org.apache.commons.lang3.CharEncoding.UTF_8;

/**
 * @author nacos
 */
public class SystemUtils {

    private static final Logger logger = LoggerFactory.getLogger(SystemUtils.class);

    /**
     * The System property name of  Standalone mode
     */
    public static final String STANDALONE_MODE_PROPERTY_NAME = "nacos.standalone";

    /**
     * The System property name of prefer hostname over ip
     */
    public static final String PREFER_HOSTNAME_OVER_IP_PROPERTY_NAME = "nacos.preferHostnameOverIp";
    /**
     * Flag to say that, when guessing a hostname, the hostname of the server should be used in preference to the IP
     * address reported by the OS.
     */
    public static final boolean PREFER_HOSTNAME_OVER_IP = Boolean.getBoolean(PREFER_HOSTNAME_OVER_IP_PROPERTY_NAME);

    /**
     * Standalone mode or not
     */
    public static final boolean STANDALONE_MODE = Boolean.getBoolean(STANDALONE_MODE_PROPERTY_NAME);

    private static OperatingSystemMXBean operatingSystemMXBean = (OperatingSystemMXBean)ManagementFactory
        .getOperatingSystemMXBean();

    public static final String LOCAL_IP = getHostAddress();

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
        List<String> ips = new ArrayList<String>();
        if (StringUtils.isNotEmpty(env)) {
            ips = Arrays.asList(env.split(","));
        }
        return ips;
    }

    public static String getSystemEnv(String key) {
        return System.getenv(key);
    }

    public static float getLoad() {
        return (float)operatingSystemMXBean.getSystemLoadAverage();
    }

    public static float getCPU() {
        return (float)operatingSystemMXBean.getSystemCpuLoad();
    }

    public static float getMem() {
        return (float)(1 - (double)operatingSystemMXBean.getFreePhysicalMemorySize() / (double)operatingSystemMXBean
            .getTotalPhysicalMemorySize());
    }

    private static String getHostAddress() {
        String address = System.getProperty("nacos.server.ip");
        if (StringUtils.isNotEmpty(address)) {
            return address;
        }

        address = "127.0.0.1";

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress ip = inetAddresses.nextElement();
                    // 兼容不规范网段
                    if (!ip.isLoopbackAddress() && !ip.getHostAddress().contains(":")) {
                        return ip.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("get local host address error", e);
        }

        return address;
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
        List<String> lines = IoUtils.readLines(
                new InputStreamReader(new FileInputStream(new File(CLUSTER_CONF_FILE_PATH)), UTF_8));
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
            instanceList.add(instance);
        }
        return instanceList;
    }

    public static void writeClusterConf(String content) throws IOException {
        IoUtils.writeStringToFile(new File(CLUSTER_CONF_FILE_PATH), content, UTF_8);
    }

}
