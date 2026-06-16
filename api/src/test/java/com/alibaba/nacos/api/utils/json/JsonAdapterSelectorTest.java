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

package com.alibaba.nacos.api.utils.json;

import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ServiceConfigurationError;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonAdapterSelectorTest {
    
    private static final String SERVICE_RESOURCE_NAME =
        "META-INF/services/" + NacosJsonAdapter.class.getName();
    
    @AfterEach
    void tearDown() {
        System.clearProperty(JsonUtils.ADAPTER_PROPERTY_NAME);
    }
    
    @Test
    void testSelectOnlyAvailableAdapter() {
        FakeAdapter adapter = new FakeAdapter("custom", true);
        
        assertSame(adapter, new JsonAdapterSelector(Collections.singletonList(adapter)).select());
    }
    
    @Test
    void testSelectJackson3WhenJackson2AndJackson3Available() {
        FakeAdapter jackson2 = new FakeAdapter(NacosJsonAdapterNames.JACKSON2, true);
        FakeAdapter jackson3 = new FakeAdapter(NacosJsonAdapterNames.JACKSON3, true);
        
        NacosJsonAdapter selected =
            new JsonAdapterSelector(Arrays.<NacosJsonAdapter>asList(jackson2, jackson3)).select();
        
        assertSame(jackson3, selected);
    }
    
    @Test
    void testSelectJackson2WhenExplicitlyConfigured() {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON2);
        FakeAdapter jackson2 = new FakeAdapter(NacosJsonAdapterNames.JACKSON2, true);
        FakeAdapter jackson3 = new FakeAdapter(NacosJsonAdapterNames.JACKSON3, true);
        
        NacosJsonAdapter selected =
            new JsonAdapterSelector(Arrays.<NacosJsonAdapter>asList(jackson2, jackson3)).select();
        
        assertSame(jackson2, selected);
    }
    
    @Test
    void testSelectJackson3WhenExplicitlyConfigured() {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON3);
        FakeAdapter jackson2 = new FakeAdapter(NacosJsonAdapterNames.JACKSON2, true);
        FakeAdapter jackson3 = new FakeAdapter(NacosJsonAdapterNames.JACKSON3, true);
        
        NacosJsonAdapter selected =
            new JsonAdapterSelector(Arrays.<NacosJsonAdapter>asList(jackson2, jackson3)).select();
        
        assertSame(jackson3, selected);
    }
    
    @Test
    void testFailWhenNoAdapterAvailable() {
        NacosLoadException exception = assertThrows(NacosLoadException.class,
            () -> new JsonAdapterSelector(Collections.<NacosJsonAdapter>emptyList()).select());
        
        assertTrue(exception.getMessage().contains("No available JSON adapter"));
    }
    
    @Test
    void testFailWhenSelectedAdapterUnavailable() {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON3);
        FakeAdapter jackson2 = new FakeAdapter(NacosJsonAdapterNames.JACKSON2, true);
        FakeAdapter jackson3 = new FakeAdapter(NacosJsonAdapterNames.JACKSON3, false);
        
        NacosLoadException exception = assertThrows(NacosLoadException.class,
            () -> new JsonAdapterSelector(Arrays.<NacosJsonAdapter>asList(jackson2, jackson3))
                .select());
        
        assertTrue(exception.getMessage().contains("jackson3"));
    }
    
    @Test
    void testBrokenAdapterIsIgnoredAndDiagnosed() {
        BrokenAdapter brokenAdapter = new BrokenAdapter();
        FakeAdapter jackson2 = new FakeAdapter(NacosJsonAdapterNames.JACKSON2, true);
        
        NacosJsonAdapter selected = new JsonAdapterSelector(
            Arrays.<NacosJsonAdapter>asList(brokenAdapter, jackson2)).select();
        
        assertSame(jackson2, selected);
    }
    
    @Test
    void testServiceConfigurationErrorAdapterIsIgnoredAndDiagnosed() {
        ServiceConfigurationErrorAdapter brokenAdapter = new ServiceConfigurationErrorAdapter();
        
        NacosLoadException exception = assertThrows(NacosLoadException.class,
            () -> new JsonAdapterSelector(
                Collections.<NacosJsonAdapter>singletonList(brokenAdapter))
                .select());
        
        assertTrue(exception.getMessage().contains("Adapter diagnostics"));
        assertTrue(exception.getMessage().contains("broken-service"));
        assertTrue(exception.getMessage().contains(ServiceConfigurationError.class.getName()));
    }
    
    @Test
    void testRuntimeExceptionAdapterIsIgnoredAndDiagnosed() {
        RuntimeExceptionAdapter brokenAdapter = new RuntimeExceptionAdapter();
        
        NacosLoadException exception = assertThrows(NacosLoadException.class,
            () -> new JsonAdapterSelector(
                Collections.<NacosJsonAdapter>singletonList(brokenAdapter))
                .select());
        
        assertTrue(exception.getMessage().contains("Adapter diagnostics"));
        assertTrue(exception.getMessage().contains("broken-runtime"));
        assertTrue(exception.getMessage().contains(IllegalStateException.class.getName()));
    }
    
    @Test
    void testFailWhenMultipleNonPreferredAdaptersAvailable() {
        FakeAdapter custom1 = new FakeAdapter("custom1", true);
        FakeAdapter custom2 = new FakeAdapter("custom2", true);
        
        NacosLoadException exception = assertThrows(NacosLoadException.class,
            () -> new JsonAdapterSelector(Arrays.<NacosJsonAdapter>asList(custom1, custom2))
                .select());
        
        assertTrue(exception.getMessage().contains("Multiple JSON adapters are available"));
    }
    
    @Test
    void testDefaultConstructorLoadsServiceProviderAndRecordsProviderFailure() throws Exception {
        File serviceRoot = new File("target/json-adapter-service-loader");
        File serviceDirectory = new File(serviceRoot, "META-INF/services");
        Files.createDirectories(serviceDirectory.toPath());
        File serviceFile = new File(serviceDirectory, NacosJsonAdapter.class.getName());
        String providers = ServiceLoadedAdapter.class.getName() + System.lineSeparator()
            + "com.alibaba.nacos.api.utils.json.MissingJsonAdapter" + System.lineSeparator();
        Files.write(serviceFile.toPath(), providers.getBytes(StandardCharsets.UTF_8));
        
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        URLClassLoader serviceClassLoader =
            new URLClassLoader(new URL[] {serviceRoot.toURI().toURL()}, originalClassLoader);
        try {
            Thread.currentThread().setContextClassLoader(serviceClassLoader);
            System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON2);
            
            NacosLoadException exception =
                assertThrows(NacosLoadException.class, () -> new JsonAdapterSelector().select());
            
            assertTrue(exception.getMessage().contains("Adapter diagnostics"));
            assertTrue(exception.getMessage().contains("provider:"));
            assertTrue(exception.getMessage().contains("MissingJsonAdapter"));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            serviceClassLoader.close();
        }
    }
    
    @Test
    void testDefaultConstructorRecordsServiceLoaderLinkageFailure() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread()
            .setContextClassLoader(new ServiceResourceLinkageErrorClassLoader(originalClassLoader));
        try {
            NacosLoadException exception =
                assertThrows(NacosLoadException.class, () -> new JsonAdapterSelector().select());
            
            assertTrue(exception.getMessage().contains("Adapter diagnostics"));
            assertTrue(exception.getMessage().contains("provider:"));
            assertTrue(exception.getMessage().contains("service-resource"));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
    
    @Test
    void testFailWhenUnsupportedAdapterConfigured() {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, "other");
        
        NacosLoadException exception = assertThrows(NacosLoadException.class,
            () -> new JsonAdapterSelector(Collections.<NacosJsonAdapter>emptyList()).select());
        
        assertTrue(exception.getMessage().contains("Unsupported JSON adapter"));
    }
    
    private static class FakeAdapter implements NacosJsonAdapter {
        
        private final String name;
        
        private final boolean available;
        
        FakeAdapter(String name, boolean available) {
            this.name = name;
            this.available = available;
        }
        
        @Override
        public String name() {
            return name;
        }
        
        @Override
        public boolean isAvailable() {
            return available;
        }
        
        @Override
        public String toJson(Object obj) {
            return null;
        }
        
        @Override
        public byte[] toJsonBytes(Object obj) {
            return new byte[0];
        }
        
        @Override
        public String toCanonicalJson(Object obj) {
            return null;
        }
        
        @Override
        public <T> T toObj(byte[] json, Class<T> cls) {
            return null;
        }
        
        @Override
        public <T> T toObj(byte[] json, Type type) {
            return null;
        }
        
        @Override
        public <T> T toObj(byte[] json, NacosTypeReference<T> typeReference) {
            return null;
        }
        
        @Override
        public <T> T toObj(String json, Class<T> cls) {
            return null;
        }
        
        @Override
        public <T> T toObj(String json, Type type) {
            return null;
        }
        
        @Override
        public <T> T toObj(String json, NacosTypeReference<T> typeReference) {
            return null;
        }
        
        @Override
        public <T> T toObj(InputStream inputStream, Class<T> cls) {
            return null;
        }
        
        @Override
        public <T> T toObj(InputStream inputStream, Type type) {
            return null;
        }
        
        @Override
        public void registerSubtype(NacosJsonSubtype subtype) {
        }
    }
    
    private static class BrokenAdapter extends FakeAdapter {
        
        BrokenAdapter() {
            super("broken", true);
        }
        
        @Override
        public boolean isAvailable() {
            throw new NoClassDefFoundError("missing");
        }
    }
    
    private static class ServiceConfigurationErrorAdapter extends FakeAdapter {
        
        ServiceConfigurationErrorAdapter() {
            super("broken-service", true);
        }
        
        @Override
        public boolean isAvailable() {
            throw new ServiceConfigurationError("broken service");
        }
    }
    
    private static class RuntimeExceptionAdapter extends FakeAdapter {
        
        RuntimeExceptionAdapter() {
            super("broken-runtime", true);
        }
        
        @Override
        public boolean isAvailable() {
            throw new IllegalStateException("broken runtime");
        }
    }
    
    private static class ServiceResourceLinkageErrorClassLoader extends ClassLoader {
        
        ServiceResourceLinkageErrorClassLoader(ClassLoader parent) {
            super(parent);
        }
        
        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SERVICE_RESOURCE_NAME.equals(name)) {
                throw new NoClassDefFoundError("service-resource");
            }
            return super.getResources(name);
        }
    }
}
