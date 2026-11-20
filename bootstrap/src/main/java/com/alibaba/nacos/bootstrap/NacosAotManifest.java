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

package com.alibaba.nacos.bootstrap;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.net.URL;

/**
 * Structural build choices shared by the AOT processor and runtime validation.
 * Connection details and secrets deliberately remain runtime configuration.
 */
final class NacosAotManifest {
    
    static final String FORMAT = "2";
    
    static final String DATASOURCE = "datasource";
    
    static final String[] SERVICES = {
        "com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect",
        "com.alibaba.nacos.plugin.datasource.mapper.Mapper",
        "java.sql.Driver",
        "com.alibaba.nacos.api.plugin.PluginProvider",
        "com.alibaba.nacos.sys.filter.NacosPackageExcludeFilter",
        "com.alibaba.nacos.core.listener.startup.NacosStartUp"
    };
    
    private NacosAotManifest() {
    }
    
    static Properties snapshot(Environment environment, ClassLoader classLoader) {
        Properties result = new Properties();
        result.setProperty("format", FORMAT);
        result.setProperty("deployment", EnvUtil.getDeploymentType().name());
        boolean standalone = EnvUtil.getStandaloneMode();
        boolean embedded = Boolean.getBoolean("embeddedStorage");
        result.setProperty("standalone", Boolean.toString(standalone));
        result.setProperty("embeddedStorage", Boolean.toString(embedded));
        result.setProperty("functionMode", environment.getProperty("nacos.function.mode", "All"));
        String platform = environment.getProperty("nacos.plugin.datasource-dialect.type");
        if (!StringUtils.hasText(platform)) {
            platform = environment.getProperty("spring.sql.init.platform");
        }
        if (!StringUtils.hasText(platform)) {
            platform = standalone || embedded ? "derby" : "mysql";
        }
        result.setProperty("datasource", platform.trim().toLowerCase(Locale.ROOT));
        result.setProperty("profiles",
            String.join(",", new TreeSet<>(Arrays.asList(environment.getActiveProfiles()))));
        for (String key : new String[] {"nacos.ai.mcp.registry.enabled",
            "nacos.ai.skill.registry.enabled", "nacos.ai.ard.enabled"}) {
            result.setProperty(key, environment.getProperty(key, Boolean.class, false).toString());
        }
        result.setProperty("nacos.ai.resource.search.enabled",
            environment.getProperty("nacos.ai.resource.search.enabled", Boolean.class, true)
                .toString());
        for (String service : SERVICES) {
            result.setProperty("spi." + service, providers(classLoader, service));
        }
        return result;
    }
    
    static void validate(Properties manifest, Environment environment, ClassLoader classLoader) {
        if (!FORMAT.equals(manifest.getProperty("format"))) {
            throw new IllegalStateException(
                "Unsupported Nacos AOT manifest format; regenerate AOT artifacts");
        }
        Properties actual = snapshot(environment, classLoader);
        for (String key : new TreeSet<>(actual.stringPropertyNames())) {
            if (DATASOURCE.equals(key)) {
                validateDatasource(manifest, actual.getProperty(key));
            } else if (!actual.getProperty(key).equals(manifest.getProperty(key))) {
                throw new IllegalStateException("Nacos AOT artifact mismatch for " + key
                    + "; regenerate AOT artifacts with the runtime configuration and plugins");
            }
        }
    }
    
    private static void validateDatasource(Properties manifest, String platform) {
        String accepted = manifest.getProperty(DATASOURCE);
        if (!supportsDatasource(accepted, platform)) {
            throw new IllegalStateException("Nacos AOT artifact mismatch for " + DATASOURCE
                + ": the artifact accepts [" + accepted + "] but the runtime uses " + platform
                + "; regenerate AOT artifacts with the runtime configuration and plugins");
        }
    }
    
    static boolean hasAiRegistry(Environment environment) {
        return environment.getProperty("nacos.ai.mcp.registry.enabled", Boolean.class, false)
            || environment.getProperty("nacos.ai.skill.registry.enabled", Boolean.class, false)
            || environment.getProperty("nacos.ai.ard.enabled", Boolean.class, false);
    }
    
    /**
     * Whether an artifact that accepts the comma-separated platforms in {@code accepted} can run with the
     * datasource {@code platform} of the runtime configuration.
     *
     * @param accepted the accepted platforms recorded in the artifact
     * @param platform the normalized datasource platform of the runtime configuration
     * @return {@code true} when {@code platform} is one of the accepted platforms
     */
    static boolean supportsDatasource(String accepted, String platform) {
        if (accepted == null || platform == null) {
            return false;
        }
        for (String candidate : accepted.split(",")) {
            if (candidate.trim().equals(platform)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Returns the platforms that a generated artifact accepts at run time: the platform of the generation
     * configuration and the platforms declared for the build. Names are trimmed and lower-cased, blank
     * entries are ignored, and the result is sorted. A build without declared platforms keeps the single
     * value that it recorded before.
     *
     * @param generationPlatform the datasource platform of the configuration used for generation
     * @param declared           comma-separated platforms declared for the build, or {@code null}
     * @return the accepted platforms as a comma-separated list
     */
    static String acceptedDatasources(String generationPlatform, String declared) {
        Set<String> platforms = new TreeSet<>();
        addDatasource(platforms, generationPlatform);
        if (declared != null) {
            for (String platform : declared.split(",")) {
                addDatasource(platforms, platform);
            }
        }
        return String.join(",", platforms);
    }
    
    private static void addDatasource(Set<String> platforms, String platform) {
        if (StringUtils.hasText(platform)) {
            platforms.add(platform.trim().toLowerCase(Locale.ROOT));
        }
    }
    
    private static String providers(ClassLoader classLoader, String service) {
        Set<String> providers = new TreeSet<>();
        try {
            Enumeration<URL> resources = classLoader.getResources("META-INF/services/" + service);
            while (resources.hasMoreElements()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    resources.nextElement().openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String provider = line.split("#", 2)[0].trim();
                        if (!provider.isEmpty()) {
                            providers.add(provider);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot inspect Nacos AOT SPI: " + service, e);
        }
        return String.join(",", providers);
    }
}
