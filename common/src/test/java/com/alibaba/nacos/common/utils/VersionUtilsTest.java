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

import java.io.File;
import java.net.URL;

public class VersionUtilsTest {
    
    @Test
    public void testVersionCompareLt() {
        Assert.assertTrue(VersionUtils.compareVersion("1.2.0", "1.2.1") < 0);
        Assert.assertTrue(VersionUtils.compareVersion("0.2.0", "1.2.0") < 0);
        Assert.assertTrue(VersionUtils.compareVersion("1.2.0", "1.3.0") < 0);
    }
    
    @Test
    public void testVersionCompareGt() {
        Assert.assertTrue(VersionUtils.compareVersion("1.2.2", "1.2.1") > 0);
        Assert.assertTrue(VersionUtils.compareVersion("2.2.0", "1.2.0") > 0);
        Assert.assertTrue(VersionUtils.compareVersion("1.3.0", "1.2.0") > 0);
    }
    
    @Test
    public void testVersionCompareEt() {
        Assert.assertEquals(0, VersionUtils.compareVersion("1.2.1", "1.2.1"));
    }
    
    @Test
    public void testVersionCompareLtWithChar() {
        Assert.assertTrue(VersionUtils.compareVersion("1.2.0-beta", "1.2.1") < 0);
    }
    
    @Test
    public void testVersionCompareGtWithChar() {
        Assert.assertTrue(VersionUtils.compareVersion("1.2.2-beta", "1.2.1-beta") > 0);
    }
    
    @Test
    public void testVersionCompareEtWithChar() {
        Assert.assertEquals(0, VersionUtils.compareVersion("1.2.1", "1.2.1-beta"));
    }
    
    @Test
    public void testVersionCompareResourceNotExist() {
        URL resource = VersionUtils.class.getClassLoader().getResource("nacos-version.txt");
        Assert.assertNotNull(resource);
        File originFile = new File(resource.getFile());
        File tempFile = new File(originFile.getAbsolutePath() + ".rename");
        Assert.assertTrue(originFile.renameTo(tempFile));
    
        // not throw any exception
        VersionUtils.compareVersion("1.2.1", "1.2.1");

        Assert.assertTrue(tempFile.renameTo(originFile));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testVersionCompareVersionNotValid1() {
        VersionUtils.compareVersion("1.2.1.1", "1.2.1.1");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testVersionCompareVersionNotValid2() {
        VersionUtils.compareVersion("1.2.1", "1.2.1.1");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testVersionCompareVersionNotValid3() {
        VersionUtils.compareVersion("1.2.1.1", "1.2.1");
    }
    
}