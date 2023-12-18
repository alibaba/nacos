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

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(SpringJUnit4ClassRunner.class)
public class DiskUtilsTest {
    
    static MockedStatic<FileUtils> fileUtils;
    
    @BeforeClass
    public static void before() {
        fileUtils =  Mockito.mockStatic(FileUtils.class);
    }
    
    @AfterClass
    public static void after() {
        fileUtils.close();
    }
    
    @Test
    public void testSaveHeartBeatToDisk() throws IOException {
        String heartBeatTime = System.currentTimeMillis() + "";
        DiskUtil.saveHeartBeatToDisk(heartBeatTime);
        fileUtils.verify(() -> FileUtils.writeStringToFile(any(), eq(heartBeatTime), eq(UTF_8.displayName())), Mockito.times(1));
    }
    
    @Test
    public void testRemoveHeartHeat() {
        File targetFile = DiskUtil.heartBeatFile();
        DiskUtil.removeHeartHeat();
        fileUtils.verify(() -> FileUtils.deleteQuietly(targetFile), Mockito.times(1));
    }
    
    @Test
    public void testHeartBeatFile() {
        File file = DiskUtil.heartBeatFile();
        String[] arr = file.getPath().split("/");
        Assert.assertEquals("heartBeat.txt", arr[arr.length - 1]);
        Assert.assertEquals("status", arr[arr.length - 2]);
        Assert.assertEquals("nacos", arr[arr.length - 3]);
    }
}
