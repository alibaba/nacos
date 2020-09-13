/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.common.utils.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Unit test for LocalConfigInfoProcessor.
 * 
 * @author JackSun-Developer
 */
public class LocalConfigInfoProcessorTest {
    
    private String tempJmLogPath = null;
    
    private String tempJmSnapshotPath = null;
    
    private String tempNacosCacheDir = null;
    
    private String tempUserHome = null;
    
    @Before
    public void setUp() throws Exception {
        tempJmLogPath = System.getProperty("JM.LOG.PATH");
        tempJmSnapshotPath = System.getProperty("JM.SNAPSHOT.PATH");
        tempNacosCacheDir = System.getProperty("nacos.cache.dir");
        tempUserHome = System.getProperty("user.home");
    }
    
    @Test
    public void testCache() throws Exception {
        if (!StringUtils.isBlank(tempJmLogPath)) {
            /* Case 1, "jm.log.path" is set. */ 
            assertEquals(LocalConfigInfoProcessor.LOCAL_FILEROOT_PATH, tempJmLogPath
                    + File.separator + "nacos" + File.separator + "config");
        } else {
            if (!StringUtils.isBlank(tempNacosCacheDir)) {
                /* Case 2, "nacos.cache.dir" is set. */
                assertEquals(LocalConfigInfoProcessor.LOCAL_FILEROOT_PATH, tempNacosCacheDir
                        + File.separator + "config");
            } else {
                /* Case 3, all of the above properties are not set. */
                assertEquals(LocalConfigInfoProcessor.LOCAL_FILEROOT_PATH, tempUserHome
                        + File.separator + "nacos" + File.separator + "config");
            }
        }
        
        if (!StringUtils.isBlank(tempJmSnapshotPath)) {
            /* Case 1, "jm.snapshot.path" is set. */
            assertEquals(LocalConfigInfoProcessor.LOCAL_SNAPSHOT_PATH, tempJmSnapshotPath
                    + File.separator + "nacos" + File.separator + "config");
        } else {
            if (!StringUtils.isBlank(tempNacosCacheDir)) {
                /* Case 2, "nacos.cache.dir" is set. */
                assertEquals(LocalConfigInfoProcessor.LOCAL_SNAPSHOT_PATH, tempNacosCacheDir
                        + File.separator + "config");
            } else {
                /* Case 3, all of the above properties are not set. */
                assertEquals(LocalConfigInfoProcessor.LOCAL_SNAPSHOT_PATH, tempUserHome
                        + File.separator + "nacos" + File.separator + "config");
            }
        }
    }
}