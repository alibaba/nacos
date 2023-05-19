/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.base;

import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.alibaba.nacos.sys.utils.DiskUtils;

import java.io.File;
import java.io.IOException;

/**
 * Cache files to clear tool classes.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigCleanUtils {
    
    public static void cleanClientCache() throws IOException {
        DiskUtils.deleteDirThenMkdir(LocalConfigInfoProcessor.LOCAL_SNAPSHOT_PATH);
    }
    
    /**
     * Change test env to new nacos home.
     *
     * @param caseName test case name
     */
    public static void changeToNewTestNacosHome(String caseName) {
        String userHome = System.getProperty("user.home");
        String testNacosHome = userHome + File.separator + "nacos" + File.separator + caseName;
        System.setProperty("nacos.home", testNacosHome);
    }
    
}
