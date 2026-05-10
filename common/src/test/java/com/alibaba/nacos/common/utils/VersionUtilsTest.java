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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void testVersionCompareTwoDigitMinorIsGreaterThanSingleDigit() {
        assertTrue(VersionUtils.compareVersion("1.10.0", "1.9.0") > 0);
        assertTrue(VersionUtils.compareVersion("1.9.0", "1.10.0") < 0);
    }
    
    @Test
    void testVersionCompareTwoDigitMajorIsGreaterThanSingleDigit() {
        assertTrue(VersionUtils.compareVersion("10.0.0", "9.0.0") > 0);
        assertTrue(VersionUtils.compareVersion("9.0.0", "10.0.0") < 0);
    }
    
    @Test
    void testVersionCompareTwoDigitPatchIsGreaterThanSingleDigit() {
        assertTrue(VersionUtils.compareVersion("1.0.10", "1.0.9") > 0);
        assertTrue(VersionUtils.compareVersion("1.0.9-beta", "1.0.10-beta") < 0);
    }
    
    @Test
    void testVersionCompareNonNumericPartIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> VersionUtils.compareVersion("1.x.0", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> VersionUtils.compareVersion("1.0.0", "a.b.c"));
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
    
    // ---- Semver with pre-release tests ----
    
    @Test
    void testNormalizeSemverWithPreRelease() {
        assertEquals("0.0.1-beta", VersionUtils.normalizeSemver("0.0.1-beta"));
        assertEquals("1.2.3-alpha", VersionUtils.normalizeSemver("v1.2.3-alpha"));
        assertEquals("1.0.0-rc.1", VersionUtils.normalizeSemver("1.0.0-rc.1"));
        assertEquals("1.0.0", VersionUtils.normalizeSemver("1.0.0"));
        assertNull(VersionUtils.normalizeSemver("1.0.0-"));
        assertNull(VersionUtils.normalizeSemver("1.0.0-beta!"));
    }
    
    @Test
    void testIsSemverWithPreRelease() {
        assertTrue(VersionUtils.isSemver("0.0.1-beta"));
        assertTrue(VersionUtils.isSemver("v1.2.3-alpha"));
        assertTrue(VersionUtils.isSemver("1.0.0-rc.1"));
        assertTrue(VersionUtils.isSemver("1.0.0"));
        assertFalse(VersionUtils.isSemver("1.0.0-"));
        assertFalse(VersionUtils.isSemver("1.0"));
    }
    
    @Test
    void testParseSemverPartsWithPreRelease() {
        assertArrayEquals(new int[] {0, 0, 1}, VersionUtils.parseSemverParts("0.0.1-beta"));
        assertArrayEquals(new int[] {1, 2, 3}, VersionUtils.parseSemverParts("v1.2.3-alpha"));
        assertArrayEquals(new int[] {1, 0, 0}, VersionUtils.parseSemverParts("1.0.0-rc.1"));
        assertArrayEquals(new int[] {1, 0, 0}, VersionUtils.parseSemverParts("1.0.0"));
    }
    
    @Test
    void testCompareSemverVersionPreReleaseLowerThanRelease() {
        assertTrue(VersionUtils.compareSemverVersion("1.0.0-beta", "1.0.0") < 0);
        assertTrue(VersionUtils.compareSemverVersion("1.0.0", "1.0.0-beta") > 0);
    }
    
    @Test
    void testCompareSemverVersionBothPreRelease() {
        assertTrue(VersionUtils.compareSemverVersion("1.0.0-alpha", "1.0.0-beta") < 0);
        assertEquals(0, VersionUtils.compareSemverVersion("1.0.0-beta", "1.0.0-beta"));
        assertTrue(VersionUtils.compareSemverVersion("1.0.0-rc", "1.0.0-beta") > 0);
    }
    
    @Test
    void testCompareSemverVersionDifferentNumericWithPreRelease() {
        assertTrue(VersionUtils.compareSemverVersion("0.0.1-beta", "0.0.2") < 0);
        assertTrue(VersionUtils.compareSemverVersion("1.0.0-beta", "0.9.9") > 0);
    }
    
    @Test
    void testNextSemverPatchWithPreRelease() {
        assertEquals("1.2.4", VersionUtils.nextSemverPatch("1.2.3-beta"));
        assertEquals("0.0.2", VersionUtils.nextSemverPatch("0.0.1-alpha"));
    }
    
    @Test
    void testMaxSemverWithPreRelease() {
        List<String> versions = Arrays.asList("0.0.1-beta", "0.0.1", "0.0.2-alpha", "v1");
        assertEquals("0.0.2-alpha", VersionUtils.maxSemver(versions));
    }
    
    @Test
    void testMaxSemverReleaseBeatsPreRelease() {
        List<String> versions = Arrays.asList("1.0.0-beta", "1.0.0");
        assertEquals("1.0.0", VersionUtils.maxSemver(versions));
    }
    
    @Test
    void testIsSupportedVersionFormatWithPreRelease() {
        assertTrue(VersionUtils.isSupportedVersionFormat("0.0.1-beta"));
        assertTrue(VersionUtils.isSupportedVersionFormat("1.0.0-rc.1"));
        assertTrue(VersionUtils.isSupportedVersionFormat("v1"));
        assertFalse(VersionUtils.isSupportedVersionFormat("abc"));
    }
    
    @Test
    void testIsGreaterVersionWithPreRelease() {
        assertTrue(VersionUtils.isGreaterVersion("1.0.0", "1.0.0-beta"));
        assertFalse(VersionUtils.isGreaterVersion("1.0.0-alpha", "1.0.0-beta"));
        assertTrue(VersionUtils.isGreaterVersion("1.0.1-beta", "1.0.0"));
    }
    
    // ---- VNumber (legacy vN format) tests ----
    
    @Test
    @DisplayName("parseVNumber should return numeric value for valid vN format")
    void testParseVNumberShouldReturnNumeric() {
        assertEquals(3, VersionUtils.parseVNumber("v3"));
        assertEquals(10, VersionUtils.parseVNumber("V10"));
        assertEquals(1, VersionUtils.parseVNumber("v1"));
    }
    
    @Test
    @DisplayName("parseVNumber with invalid format should return null")
    void testParseVNumberWithInvalidFormatShouldReturnNull() {
        assertNull(VersionUtils.parseVNumber("3"));
        assertNull(VersionUtils.parseVNumber("version3"));
        assertNull(VersionUtils.parseVNumber("v"));
        assertNull(VersionUtils.parseVNumber("v0"));
        assertNull(VersionUtils.parseVNumber("v-1"));
        assertNull(VersionUtils.parseVNumber(null));
        assertNull(VersionUtils.parseVNumber(""));
    }
    
    @Test
    @DisplayName("isVNumber should return true for valid vN format")
    void testIsVNumberShouldReturnTrueForVFormat() {
        assertTrue(VersionUtils.isVNumber("v1"));
        assertTrue(VersionUtils.isVNumber("V10"));
        assertTrue(VersionUtils.isVNumber("v100"));
    }
    
    @Test
    @DisplayName("isVNumber with invalid format should return false")
    void testIsVNumberWithInvalidFormatShouldReturnFalse() {
        assertFalse(VersionUtils.isVNumber("1"));
        assertFalse(VersionUtils.isVNumber("version1"));
        assertFalse(VersionUtils.isVNumber("v"));
        assertFalse(VersionUtils.isVNumber("1.0.0"));
    }
    
    @Test
    @DisplayName("maxVNumber should return highest number")
    void testMaxVNumberShouldReturnHighestNumber() {
        List<String> versions = Arrays.asList("v1", "v5", "v3");
        assertEquals(5, VersionUtils.maxVNumber(versions));
    }
    
    @Test
    @DisplayName("maxVNumber with empty list should return 0")
    void testMaxVNumberWithEmptyListShouldReturnZero() {
        assertEquals(0, VersionUtils.maxVNumber(Collections.emptyList()));
        assertEquals(0, VersionUtils.maxVNumber(null));
    }
    
    @Test
    @DisplayName("maxVNumberVersion should return version string with highest number")
    void testMaxVNumberVersionShouldReturnHighestVersion() {
        List<String> versions = Arrays.asList("v1", "v5", "v3");
        assertEquals("v5", VersionUtils.maxVNumberVersion(versions));
    }
    
    @Test
    @DisplayName("maxVNumberVersion with no valid versions should return null")
    void testMaxVNumberVersionWithNoValidVersionsShouldReturnNull() {
        assertNull(VersionUtils.maxVNumberVersion(Collections.emptyList()));
        assertNull(VersionUtils.maxVNumberVersion(Arrays.asList("1.0.0", "invalid")));
    }
    
    @Test
    @DisplayName("nextVNumberVersion should increment highest vN")
    void testNextVNumberVersionShouldIncrement() {
        List<String> versions = Arrays.asList("v1", "v3", "v2");
        assertEquals("v4", VersionUtils.nextVNumberVersion(versions));
    }
    
    @Test
    @DisplayName("nextVNumberVersion with empty list should return v1")
    void testNextVNumberVersionWithEmptyListShouldReturnV1() {
        assertEquals("v1", VersionUtils.nextVNumberVersion(Collections.emptyList()));
        assertEquals("v1", VersionUtils.nextVNumberVersion(null));
    }
    
    @Test
    @DisplayName("isGreaterVersion with vN format should compare correctly")
    void testIsGreaterVersionWithVNumberFormat() {
        assertTrue(VersionUtils.isGreaterVersion("v3", "v2"));
        assertFalse(VersionUtils.isGreaterVersion("v1", "v2"));
        assertFalse(VersionUtils.isGreaterVersion("v1", "1.0.0")); // incompatible formats
    }
}
