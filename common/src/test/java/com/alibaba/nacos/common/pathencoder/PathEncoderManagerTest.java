/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.pathencoder;

import com.alibaba.nacos.common.pathencoder.impl.WindowsEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PathEncoderManagerTest {
    
    private String cachedOsName;
    
    private Field targetEncoder;
    
    private Object cachedEncoder;
    
    @BeforeEach
    void setUp() throws Exception {
        cachedOsName = System.getProperty("os.name");
        targetEncoder = PathEncoderManager.class.getDeclaredField("targetEncoder");
        targetEncoder.setAccessible(true);
        cachedEncoder = targetEncoder.get(PathEncoderManager.getInstance());
    }
    
    @AfterEach
    void tearDown() throws Exception {
        System.setProperty("os.name", cachedOsName);
        targetEncoder.set(PathEncoderManager.getInstance(), cachedEncoder);
    }
    
    @Test
    void testInitWithWindows()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<PathEncoderManager> constructor = PathEncoderManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        System.setProperty("os.name", "window");
        PathEncoderManager instance = constructor.newInstance();
        assertTrue(targetEncoder.get(instance) instanceof WindowsEncoder);
    }
    
    /**
     * test expose method.
     */
    @Test
    void testWindowsEncode() throws Exception {
        // load static
        PathEncoderManager instance = PathEncoderManager.getInstance();
        String case1 = "aa||a";
        String case2 = "aa%A9%%A9%a";
        // try to encode if in windows
        targetEncoder.set(instance, new WindowsEncoder());
        assertEquals(PathEncoderManager.getInstance().encode(case1), case2);
        assertEquals(PathEncoderManager.getInstance().decode(case2), case1);
    }
    
    @Test
    void testEncodeWithNonExistOs() throws Exception {
        // load static
        PathEncoderManager instance = PathEncoderManager.getInstance();
        // remove impl
        targetEncoder.set(instance, null);
        // try to encode, non windows
        String case1 = "aa||a";
        assertEquals(PathEncoderManager.getInstance().encode(case1), case1);
        String case2 = "aa%A9%%A9%a";
        assertEquals(PathEncoderManager.getInstance().decode(case2), case2);
    }
    
    @Test
    void testEncodeForNull() throws IllegalAccessException {
        PathEncoder mockPathEncoder = mock(PathEncoder.class);
        targetEncoder.set(PathEncoderManager.getInstance(), mockPathEncoder);
        assertNull(PathEncoderManager.getInstance().encode(null));
        assertNull(PathEncoderManager.getInstance().decode(null));
        verify(mockPathEncoder, never()).encode(null, Charset.defaultCharset().name());
        verify(mockPathEncoder, never()).decode(null, Charset.defaultCharset().name());
    }
}
