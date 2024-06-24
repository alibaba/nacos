/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionUtilsTest {
    
    @Test
    void testVersionCompareLt() {
        assertTrue(VersionUtils.compareVersion("1.2.0", "1.2.1") < 0);
        assertTrue(VersionUtils.compareVersion("0.2.0", "1.2.0") < 0);
        assertTrue(VersionUtils.compareVersion("1.2.0", "1.3.0") < 0);
    }
    
    @Test
    void testVersionCompareGt() {
        assertTrue(VersionUtils.compareVersion("1.2.2", "1.2.1") > 0);
        assertTrue(VersionUtils.compareVersion("2.2.0", "1.2.0") > 0);
        assertTrue(VersionUtils.compareVersion("1.3.0", "1.2.0") > 0);
    }
    
    @Test
    void testVersionCompareEt() {
        assertEquals(0, VersionUtils.compareVersion("1.2.1", "1.2.1"));
    }
    
    @Test
    void testVersionCompareLtWithChar() {
        assertTrue(VersionUtils.compareVersion("1.2.0-beta", "1.2.1") < 0);
    }
    
    @Test
    void testVersionCompareGtWithChar() {
        assertTrue(VersionUtils.compareVersion("1.2.2-beta", "1.2.1-beta") > 0);
    }
    
    @Test
    void testVersionCompareEtWithChar() {
        assertEquals(0, VersionUtils.compareVersion("1.2.1", "1.2.1-beta"));
    }
    
    @Test
    void testVersionCompareResourceNotExist() {
        URL resource = VersionUtils.class.getClassLoader().getResource("nacos-version.txt");
        assertNotNull(resource);
        File originFile = new File(resource.getFile());
        File tempFile = new File(originFile.getAbsolutePath() + ".rename");
        assertTrue(originFile.renameTo(tempFile));
        
        // not throw any exception
        VersionUtils.compareVersion("1.2.1", "1.2.1");
        
        assertTrue(tempFile.renameTo(originFile));
    }
    
    @Test
    void testVersionCompareVersionNotValid1() {
        assertThrows(IllegalArgumentException.class, () -> {
            VersionUtils.compareVersion("1.2.1.1", "1.2.1.1");
        });
    }
    
    @Test
    void testVersionCompareVersionNotValid2() {
        assertThrows(IllegalArgumentException.class, () -> {
            VersionUtils.compareVersion("1.2.1", "1.2.1.1");
        });
    }
    
    @Test
    void testVersionCompareVersionNotValid3() {
        assertThrows(IllegalArgumentException.class, () -> {
            VersionUtils.compareVersion("1.2.1.1", "1.2.1");
        });
    }
    
    @Test
    void testFullClientVersion() {
        assertNotNull(VersionUtils.getFullClientVersion());
        assertTrue(VersionUtils.getFullClientVersion().startsWith("Nacos-Java-Client:v"));
    }
}