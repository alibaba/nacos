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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;

public class DiskUtilsTest {
    
    static MockedStatic<FileUtils> fileUtils;
    
    @BeforeClass
    public static void before() {
        fileUtils =  Mockito.mockStatic(FileUtils.class);
    }
    
    @Test
    public void testSaveHeartBeatToDisk() throws IOException {
        String heartBeatTime = System.currentTimeMillis() + "";
        DiskUtil.saveHeartBeatToDisk(heartBeatTime);
        fileUtils.verify(() -> FileUtils.writeStringToFile(DiskUtil.heartBeatFile(), heartBeatTime, Constants.ENCODE), Mockito.times(1));
    }

    @Test
    public void testSaveToDisk() throws IOException {
        File targetFile = DiskUtil.targetFile("test", "test", "test");
        DiskUtil.saveToDisk("test", "test", "test", "saveToDisk");
        fileUtils.verify(() -> FileUtils.writeStringToFile(targetFile, "saveToDisk", Constants.ENCODE), Mockito.times(1));
    }

    @Test
    public void testSaveBetaToDisk() throws IOException {
        File targetFile = DiskUtil.targetBetaFile("test", "test", "test");
        DiskUtil.saveBetaToDisk("test", "test", "test", "saveBetaToDisk");
        fileUtils.verify(() -> FileUtils.writeStringToFile(targetFile, "saveBetaToDisk", Constants.ENCODE), Mockito.times(1));
    }

    @Test
    public void testSaveTagToDisk() throws IOException {
        File targetFile = DiskUtil.targetTagFile("test", "test", "test", "tag");
        DiskUtil.saveTagToDisk("test", "test", "test", "tag", "saveTagToDisk");
        fileUtils.verify(() -> FileUtils.writeStringToFile(targetFile, "saveTagToDisk", Constants.ENCODE), Mockito.times(1));
    }

    @Test
    public void testRemoveConfigInfo() {
        File targetFile = DiskUtil.targetFile("test", "test", "test");
        DiskUtil.removeConfigInfo("test", "test", "test");
        fileUtils.verify(() -> FileUtils.deleteQuietly(targetFile), Mockito.times(1));
    }

    @Test
    public void testRemoveConfigInfo4Beta() {
        File targetFile = DiskUtil.targetBetaFile("test", "test", "test");
        DiskUtil.removeConfigInfo4Beta("test", "test", "test");
        fileUtils.verify(() -> FileUtils.deleteQuietly(targetFile), Mockito.times(1));
    }

    @Test
    public void testRemoveConfigInfo4Tag() {
        File targetFile = DiskUtil.targetTagFile("test", "test", "test", "tag");
        DiskUtil.removeConfigInfo4Tag("test", "test", "test", "tag");
        fileUtils.verify(() -> FileUtils.deleteQuietly(targetFile), Mockito.times(1));
    }

    @Test
    public void testRemoveHeartHeat() {
        File targetFile = DiskUtil.heartBeatFile();
        DiskUtil.removeHeartHeat();
        fileUtils.verify(() -> FileUtils.deleteQuietly(targetFile), Mockito.times(1));
    }

    @Test
    public void testTargetFile() {
        File file = DiskUtil.targetFile("test1", "test2", "test3");
        String[] arr = file.getPath().split(File.separator);
        Assert.assertEquals("test1", arr[arr.length - 1]);
        Assert.assertEquals("test2", arr[arr.length - 2]);
        Assert.assertEquals("test3", arr[arr.length - 3]);

        File file2 = DiskUtil.targetFile("test1", "test2", "");
        String[] arr2 = file2.getPath().split(File.separator);
        Assert.assertEquals("test1", arr2[arr2.length - 1]);
        Assert.assertEquals("test2", arr2[arr2.length - 2]);
        Assert.assertEquals("config-data", arr2[arr2.length - 3]);
    }

    @Test
    public void testTargetBetaFile() {
        File file = DiskUtil.targetBetaFile("test1", "test2", "test3");
        String[] arr = file.getPath().split(File.separator);
        Assert.assertEquals("test1", arr[arr.length - 1]);
        Assert.assertEquals("test2", arr[arr.length - 2]);
        Assert.assertEquals("test3", arr[arr.length - 3]);

        File file2 = DiskUtil.targetBetaFile("test1", "test2", "");
        String[] arr2 = file2.getPath().split(File.separator);
        Assert.assertEquals("test1", arr2[arr2.length - 1]);
        Assert.assertEquals("test2", arr2[arr2.length - 2]);
        Assert.assertEquals("beta-data", arr2[arr2.length - 3]);
    }

    @Test
    public void testTargetTagFile() {
        File file = DiskUtil.targetTagFile("test1", "test2", "test3", "tag");
        String[] arr = file.getPath().split(File.separator);
        Assert.assertEquals("tag", arr[arr.length - 1]);
        Assert.assertEquals("test1", arr[arr.length - 2]);
        Assert.assertEquals("test2", arr[arr.length - 3]);
        Assert.assertEquals("test3", arr[arr.length - 4]);

        File file2 = DiskUtil.targetTagFile("test1", "test2", "", "tag");
        String[] arr2 = file2.getPath().split(File.separator);
        Assert.assertEquals("tag", arr2[arr2.length - 1]);
        Assert.assertEquals("test1", arr2[arr2.length - 2]);
        Assert.assertEquals("test2", arr2[arr2.length - 3]);
        Assert.assertEquals("tag-data", arr2[arr2.length - 4]);
    }

    @Test
    public void testGetConfig() throws IOException {
        String result = DiskUtil.getConfig("test", "test", "test");
        Assert.assertEquals("", result);
    }

    @Test
    public void testGetLocalConfigMd5() throws IOException {
        final MockedStatic<MD5Utils> md5Utils = Mockito.mockStatic(MD5Utils.class);
        Mockito.when(MD5Utils.md5Hex("", Constants.ENCODE)).thenReturn("md5");
        String result = DiskUtil.getLocalConfigMd5("test", "test", "test");
        Assert.assertEquals("md5", result);
        md5Utils.close();
    }

    @Test
    public void testHeartBeatFile() {
        File file = DiskUtil.heartBeatFile();
        String[] arr = file.getPath().split(File.separator);
        Assert.assertEquals("heartBeat.txt", arr[arr.length - 1]);
        Assert.assertEquals("status", arr[arr.length - 2]);
        Assert.assertEquals("nacos", arr[arr.length - 3]);
    }

    @Test
    public void testRelativePath() {
        String relativePath = DiskUtil.relativePath("test1", "test2");
        String[] arr = relativePath.split(File.separator);
        Assert.assertEquals("test2", arr[arr.length - 1]);
        Assert.assertEquals("test1", arr[arr.length - 2]);
    }
    
    @Test
    public void testClearAll() {
        DiskUtil.clearAll();
        File file = new File(EnvUtil.getNacosHome(), DiskUtil.BASE_DIR);
        fileUtils.verify(() -> FileUtils.deleteQuietly(file), Mockito.times(1));
        File fileTenant = new File(EnvUtil.getNacosHome(), DiskUtil.TENANT_BASE_DIR);
        fileUtils.verify(() -> FileUtils.deleteQuietly(fileTenant), Mockito.times(1));
    }
    
    @Test
    public void testClearAllBeta() {
        DiskUtil.clearAllBeta();
        File file = new File(EnvUtil.getNacosHome(), DiskUtil.BETA_DIR);
        fileUtils.verify(() -> FileUtils.deleteQuietly(file), Mockito.times(1));
        File fileTenant = new File(EnvUtil.getNacosHome(), DiskUtil.TENANT_BETA_DIR);
        fileUtils.verify(() -> FileUtils.deleteQuietly(fileTenant), Mockito.times(1));
    }
    
    @Test
    public void testClearAllTag() {
        DiskUtil.clearAllTag();
        File file = new File(EnvUtil.getNacosHome(), DiskUtil.TAG_DIR);
        fileUtils.verify(() -> FileUtils.deleteQuietly(file), Mockito.times(1));
        File fileTenant = new File(EnvUtil.getNacosHome(), DiskUtil.TENANT_TAG_DIR);
        fileUtils.verify(() -> FileUtils.deleteQuietly(fileTenant), Mockito.times(1));
    }
    
}
