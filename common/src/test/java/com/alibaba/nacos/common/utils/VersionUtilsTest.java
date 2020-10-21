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

import org.junit.Assert;
import org.junit.Test;

public class VersionUtilsTest {
    
    @Test
    public void testVersionCompareLt() {
        final String versionA = "1.2.0";
        final String versionB = "1.2.1";
        Assert.assertTrue(VersionUtils.compareVersion(versionA, versionB) < 0);
    }
    
    @Test
    public void testVersionCompareGt() {
        final String versionA = "1.2.2";
        final String versionB = "1.2.1";
        Assert.assertTrue(VersionUtils.compareVersion(versionA, versionB) > 0);
    }
    
    @Test
    public void testVersionCompareEt() {
        final String versionA = "1.2.1";
        final String versionB = "1.2.1";
        Assert.assertEquals(0, VersionUtils.compareVersion(versionA, versionB));
    }
    
    @Test
    public void testVersionCompareLtWithChar() {
        final String versionA = "1.2.0-beta";
        final String versionB = "1.2.1";
        Assert.assertTrue(VersionUtils.compareVersion(versionA, versionB) < 0);
    }
    
    @Test
    public void testVersionCompareGtWithChar() {
        final String versionA = "1.2.2-beta";
        final String versionB = "1.2.1-beta";
        Assert.assertTrue(VersionUtils.compareVersion(versionA, versionB) > 0);
    }
    
    @Test
    public void testVersionCompareEtWithChar() {
        final String versionA = "1.2.1";
        final String versionB = "1.2.1-beta";
        Assert.assertEquals(0, VersionUtils.compareVersion(versionA, versionB));
    }
    
}