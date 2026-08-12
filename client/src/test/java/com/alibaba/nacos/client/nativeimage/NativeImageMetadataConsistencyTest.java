/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.nativeimage;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consistency tests for the GraalVM native image metadata shipped by nacos-client.
 *
 * <p>{@code nacos-client} is published as a shaded jar: {@code maven-shade-plugin} relocates
 * {@code io.grpc.**}, {@code com.google.**} and friends under {@code com.alibaba.nacos.shaded.**},
 * see the {@code relocations} section of {@code client/pom.xml}. The shade plugin rewrites class
 * files, but it does not rewrite the textual content of {@code META-INF/native-image/**} metadata.
 * So every reachability entry inherited from a relocated dependency must also be declared under the
 * {@code com.alibaba.nacos.shaded.} prefix, otherwise GraalVM silently drops it and the resulting
 * native image fails at runtime.</p>
 *
 * @author wushiyuanmaimob
 */
class NativeImageMetadataConsistencyTest {
    
    /**
     * Prefix applied by {@code maven-shade-plugin} to all relocated packages.
     */
    private static final String SHADED_PREFIX = "com.alibaba.nacos.shaded.";
    
    private static final String NACOS_METADATA_DIR =
        "META-INF/native-image/com.alibaba.nacos/nacos-client/";
    
    private static final String NATIVE_IMAGE_PROPERTIES = "native-image.properties";
    
    private static final String REFLECT_CONFIG = "reflect-config.json";
    
    private static final String NATIVE_IMAGE_METADATA_ROOT = "META-INF/native-image/";
    
    private static final String INIT_AT_BUILD_TIME = "--initialize-at-build-time=";
    
    private static final String INIT_AT_RUN_TIME = "--initialize-at-run-time=";
    
    private static final String NAME_FIELD = "name";
    
    /**
     * Source prefixes of the relocations declared in {@code client/pom.xml}. Any metadata entry
     * starting with one of them is repackaged in the shaded jar and needs a mirrored entry.
     */
    private static final List<String> RELOCATED_PREFIXES = Arrays.asList("io.grpc.", "io.netty.",
        "com.google.", "io.perfmark.", "io.opencensus.", "javax.annotation.",
        "org.checkerframework.", "android.annotation.");
    
    @Test
    void testEveryInheritedInitializationArgHasShadedMirror()
        throws IOException, URISyntaxException {
        Set<String> inherited = readInitializationTargets(readRelocatedDependencyProperties());
        Set<String> mirrored = readInitializationTargets(readNacosClientProperties());
        Set<String> missing = new TreeSet<>();
        for (String each : inherited) {
            if (!mirrored.contains(SHADED_PREFIX + each)) {
                missing.add(each);
            }
        }
        assertTrue(missing.isEmpty(),
            "nacos-client is shaded, so these class initialization directives inherited from "
                + "io.grpc:grpc-netty-shaded match no class in the published jar and must be "
                + "mirrored under " + SHADED_PREFIX + " in " + NACOS_METADATA_DIR
                + NATIVE_IMAGE_PROPERTIES + ": " + missing);
    }
    
    @Test
    void testEveryRelocatedReflectConfigEntryHasShadedMirror() throws IOException {
        Set<String> names = readReflectConfigNames();
        assertFalse(names.isEmpty(), REFLECT_CONFIG + " should not be empty");
        Set<String> missing = new TreeSet<>();
        for (String each : names) {
            if (isRelocated(each) && !names.contains(SHADED_PREFIX + each)) {
                missing.add(each);
            }
        }
        assertTrue(missing.isEmpty(),
            "these " + REFLECT_CONFIG + " entries are relocated by the shade plugin and need a "
                + SHADED_PREFIX + " twin, otherwise they are dead in the published jar: "
                + missing);
    }
    
    private boolean isRelocated(String className) {
        if (className.startsWith(SHADED_PREFIX)) {
            return false;
        }
        for (String each : RELOCATED_PREFIXES) {
            if (className.startsWith(each)) {
                return true;
            }
        }
        return false;
    }
    
    private Set<String> readReflectConfigNames() throws IOException {
        Set<String> result = new LinkedHashSet<>();
        try (InputStream inputStream = openNacosMetadata(REFLECT_CONFIG)) {
            JsonNode root = JacksonUtils.toObj(inputStream, JsonNode.class);
            for (JsonNode each : root) {
                JsonNode name = each.get(NAME_FIELD);
                if (null != name) {
                    result.add(name.asText());
                }
            }
        }
        return result;
    }
    
    private InputStream openNacosMetadata(String fileName) {
        InputStream result = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream(NACOS_METADATA_DIR + fileName);
        assertNotNull(result, "missing native image metadata " + NACOS_METADATA_DIR + fileName);
        return result;
    }
    
    private List<String> readNacosClientProperties() throws IOException {
        List<String> result = new ArrayList<>();
        Enumeration<URL> urls = Thread.currentThread().getContextClassLoader()
            .getResources(NACOS_METADATA_DIR + NATIVE_IMAGE_PROPERTIES);
        while (urls.hasMoreElements()) {
            try (InputStream inputStream = urls.nextElement().openStream()) {
                result.add(readAll(inputStream));
            }
        }
        assertEquals(1, result.size(),
            "nacos-client must ship exactly one " + NACOS_METADATA_DIR + NATIVE_IMAGE_PROPERTIES
                + " mirroring the class initialization directives of its relocated dependencies");
        return result;
    }
    
    /**
     * Read every {@code native-image.properties} shipped by the relocated grpc dependency, taken
     * from the jar actually resolved on the test classpath so that a grpc upgrade is detected.
     *
     * @return raw content of each inherited {@code native-image.properties}
     * @throws IOException        if the dependency jar can not be read
     * @throws URISyntaxException if the dependency location is not a valid file URI
     */
    private List<String> readRelocatedDependencyProperties()
        throws IOException, URISyntaxException {
        URL location =
            NettyChannelBuilder.class.getProtectionDomain().getCodeSource().getLocation();
        assertNotNull(location, "cannot locate io.grpc:grpc-netty-shaded on the test classpath");
        List<String> result = new ArrayList<>();
        try (JarFile jarFile = new JarFile(new File(location.toURI()))) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(NATIVE_IMAGE_METADATA_ROOT)
                    && name.endsWith(NATIVE_IMAGE_PROPERTIES)) {
                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        result.add(readAll(inputStream));
                    }
                }
            }
        }
        assertFalse(result.isEmpty(),
            "io.grpc:grpc-netty-shaded is expected to ship native image metadata");
        return result;
    }
    
    /**
     * Extract the class or package names referenced by {@code --initialize-at-build-time} and
     * {@code --initialize-at-run-time} directives, joining the backslash continued lines first.
     */
    private Set<String> readInitializationTargets(List<String> contents) {
        Set<String> result = new TreeSet<>();
        for (String content : contents) {
            String joined = content.replace("\\\r\n", " ").replace("\\\n", " ");
            for (String token : joined.split("\\s+")) {
                String values = null;
                if (token.startsWith(INIT_AT_BUILD_TIME)) {
                    values = token.substring(INIT_AT_BUILD_TIME.length());
                } else if (token.startsWith(INIT_AT_RUN_TIME)) {
                    values = token.substring(INIT_AT_RUN_TIME.length());
                }
                if (null == values) {
                    continue;
                }
                for (String each : values.split(",")) {
                    if (!each.isEmpty()) {
                        result.add(each);
                    }
                }
            }
        }
        return result;
    }
    
    private String readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length = inputStream.read(buffer);
        while (length > 0) {
            output.write(buffer, 0, length);
            length = inputStream.read(buffer);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
