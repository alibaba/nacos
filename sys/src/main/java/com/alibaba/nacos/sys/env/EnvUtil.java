/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.sys.env;

import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.Objects;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Its own configuration information manipulation tool class.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class EnvUtil {
    
    public static final String STANDALONE_MODE_ALONE = "standalone";
    
    public static final String STANDALONE_MODE_CLUSTER = "cluster";
    
    public static final String FUNCTION_MODE_CONFIG = "config";
    
    public static final String FUNCTION_MODE_NAMING = "naming";
    
    /**
     * The key of nacos home.
     */
    public static final String NACOS_HOME_KEY = "nacos.home";
    
    private static String localAddress = "";
    
    private static int port = -1;
    
    private static Boolean isStandalone = null;
    
    private static String functionModeType = null;
    
    private static String contextPath = "";
    
    @JustForTest
    private static String confPath = "";
    
    @JustForTest
    private static String nacosHomePath = null;
    
    private static final OperatingSystemMXBean OPERATING_SYSTEM_MX_BEAN = (com.sun.management.OperatingSystemMXBean) ManagementFactory
            .getOperatingSystemMXBean();
    
    private static ConfigurableEnvironment environment;
    
    public static ConfigurableEnvironment getEnvironment() {
        return environment;
    }
    
    public static void setEnvironment(ConfigurableEnvironment environment) {
        EnvUtil.environment = environment;
    }
    
    public static String getProperty(String key) {
        return environment.getProperty(key);
    }
    
    public static String getProperty(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }
    
    public static <T> T getProperty(String key, Class<T> targetType) {
        return environment.getProperty(key, targetType);
    }
    
    public static <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        return environment.getProperty(key, targetType, defaultValue);
    }
    
    public static String getRequiredProperty(String key) throws IllegalStateException {
        return environment.getRequiredProperty(key);
    }
    
    public static <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
        return environment.getRequiredProperty(key, targetType);
    }
    
    public static String resolvePlaceholders(String text) {
        return environment.resolvePlaceholders(text);
    }
    
    public static String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
        return environment.resolveRequiredPlaceholders(text);
    }
    
    public static List<String> getPropertyList(String key) {
        List<String> valueList = new ArrayList<>();
        
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            String value = environment.getProperty(key + "[" + i + "]");
            if (org.apache.commons.lang3.StringUtils.isBlank(value)) {
                break;
            }
            
            valueList.add(value);
        }
        
        return valueList;
    }
    
    public static String getLocalAddress() {
        if (StringUtils.isBlank(localAddress)) {
            localAddress = InetUtils.getSelfIP() + ":" + getPort();
        }
        return localAddress;
    }
    
    public static void setLocalAddress(String localAddress) {
        EnvUtil.localAddress = localAddress;
    }
    
    public static int getPort() {
        if (port == -1) {
            port = getProperty("server.port", Integer.class, 8848);
        }
        return port;
    }
    
    public static void setPort(int port) {
        EnvUtil.port = port;
    }
    
    public static String getContextPath() {
        if (StringUtils.isBlank(contextPath)) {
            if (StringUtils.isBlank(contextPath)) {
                contextPath = getProperty(Constants.WEB_CONTEXT_PATH, "/nacos");
            }
            if (Constants.ROOT_WEB_CONTEXT_PATH.equals(contextPath)) {
                return StringUtils.EMPTY;
            } else {
                return contextPath;
            }
        }
        return contextPath;
    }
    
    public static void setContextPath(String contextPath) {
        EnvUtil.contextPath = contextPath;
    }
    
    @JustForTest
    public static void setIsStandalone(Boolean isStandalone) {
        EnvUtil.isStandalone = isStandalone;
    }
    
    /**
     * Standalone mode or not.
     */
    public static boolean getStandaloneMode() {
        if (Objects.isNull(isStandalone)) {
            isStandalone = Boolean.getBoolean(Constants.STANDALONE_MODE_PROPERTY_NAME);
        }
        return isStandalone;
    }
    
    /**
     * server function mode.
     */
    public static String getFunctionMode() {
        if (StringUtils.isEmpty(functionModeType)) {
            functionModeType = System.getProperty(Constants.FUNCTION_MODE_PROPERTY_NAME);
        }
        return functionModeType;
    }
    
    private static String nacosTmpDir;
    
    public static String getNacosTmpDir() {
        if (StringUtils.isBlank(nacosTmpDir)) {
            nacosTmpDir = Paths.get(getNacosHome(), "data", "tmp").toString();
        }
        return nacosTmpDir;
    }
    
    public static String getNacosHome() {
        if (StringUtils.isBlank(nacosHomePath)) {
            String nacosHome = System.getProperty(NACOS_HOME_KEY);
            if (StringUtils.isBlank(nacosHome)) {
                nacosHome = Paths.get(System.getProperty("user.home"), "nacos").toString();
            }
            return nacosHome;
        }
        // test-first
        return nacosHomePath;
    }
    
    @JustForTest
    public static void setNacosHomePath(String nacosHomePath) {
        EnvUtil.nacosHomePath = nacosHomePath;
    }
    
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
        return (float) OPERATING_SYSTEM_MX_BEAN.getSystemLoadAverage();
    }
    
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public static float getCPU() {
        return (float) OPERATING_SYSTEM_MX_BEAN.getSystemCpuLoad();
    }
    
    public static float getMem() {
        return (float) (1
                - (double) OPERATING_SYSTEM_MX_BEAN.getFreePhysicalMemorySize() / (double) OPERATING_SYSTEM_MX_BEAN
                .getTotalPhysicalMemorySize());
    }
    
    public static String getConfPath() {
        if (StringUtils.isNotBlank(EnvUtil.confPath)) {
            return EnvUtil.confPath;
        }
        EnvUtil.confPath = Paths.get(getNacosHome(), "conf").toString();
        return confPath;
    }
    
    public static void setConfPath(final String confPath) {
        EnvUtil.confPath = confPath;
    }
    
    public static String getClusterConfFilePath() {
        return Paths.get(getNacosHome(), "conf", "cluster.conf").toString();
    }
    
    /**
     * read cluster.conf to ip list.
     *
     * @return ip list.
     * @throws IOException ioexception {@link IOException}
     */
    public static List<String> readClusterConf() throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(new File(getClusterConfFilePath())),
                StandardCharsets.UTF_8)) {
            return analyzeClusterConf(reader);
        } catch (FileNotFoundException ignore) {
            List<String> tmp = new ArrayList<>();
            String clusters = EnvUtil.getMemberList();
            if (StringUtils.isNotBlank(clusters)) {
                String[] details = clusters.split(",");
                for (String item : details) {
                    tmp.add(item.trim());
                }
            }
            return tmp;
        }
    }
    
    /**
     * read file stream to ip list.
     *
     * @param reader reader
     * @return ip list.
     * @throws IOException IOException
     */
    public static List<String> analyzeClusterConf(Reader reader) throws IOException {
        List<String> instanceList = new ArrayList<String>();
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
    
    public static void writeClusterConf(String content) throws IOException {
        DiskUtils.writeFile(new File(getClusterConfFilePath()), content.getBytes(StandardCharsets.UTF_8), false);
    }
    
    public static String getMemberList() {
        String val = null;
        if (environment == null) {
            val = System.getenv("nacos.member.list");
            if (StringUtils.isBlank(val)) {
                val = System.getProperty("nacos.member.list");
            }
        } else {
            val = getProperty("nacos.member.list");
        }
        return val;
    }
    
    /**
     * load resource to map.
     *
     * @param resource resource
     * @return Map&lt;String, Object&gt;
     * @throws IOException IOException
     */
    public static Map<String, ?> loadProperties(Resource resource) throws IOException {
        return new OriginTrackedPropertiesLoader(resource).load();
    }
    
    private static final String FILE_PREFIX = "file:";
    
    public static Resource getApplicationConfFileResource() {
        Resource customResource = getCustomFileResource();
        if (customResource == null) {
            return getDefaultResource();
        }
        return customResource;
    }
    
    private static Resource getCustomFileResource() {
        String path = getProperty("spring.config.location");
        if (path == null) {
            path = EnvUtil.getNacosHome() + "/conf";
        }
        if (StringUtils.isNotBlank(path) && path.contains(FILE_PREFIX)) {
            String[] paths = path.split(",");
            path = paths[paths.length - 1].substring(FILE_PREFIX.length());
        }
        try {
            InputStream inputStream = new FileInputStream(Paths.get(path, "application.properties").toFile());
            return new InputStreamResource(inputStream);
        } catch (Exception ignore) {
        }
        return null;
    }
    
    private static Resource getDefaultResource() {
        InputStream inputStream = EnvUtil.class.getResourceAsStream("/application.properties");
        return new InputStreamResource(inputStream);
    }
    
}
