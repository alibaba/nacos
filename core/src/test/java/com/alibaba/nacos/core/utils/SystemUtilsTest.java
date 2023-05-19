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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import static com.alibaba.nacos.sys.env.Constants.PREFER_HOSTNAME_OVER_IP_PROPERTY_NAME;
import static com.alibaba.nacos.sys.env.Constants.STANDALONE_MODE_PROPERTY_NAME;

/**
 * {@link ApplicationUtils} Test.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.2
 */
public class SystemUtilsTest {
    
    private static final Random RANDOM = new Random();
    
    private static boolean standaloneMode = RANDOM.nextBoolean();
    
    private static boolean preferHostMode = RANDOM.nextBoolean();
    
    @BeforeClass
    public static void init() {
        System.setProperty("nacos.standalone", String.valueOf(standaloneMode));
        System.setProperty("nacos.preferHostnameOverIp", String.valueOf(preferHostMode));
    }
    
    @Test
    public void testStandaloneModeConstants() {
        
        System.out.printf("System property \"%s\" = %s \n", "nacos.standalone", standaloneMode);
        
        if ("true".equalsIgnoreCase(System.getProperty("nacos.standalone"))) {
            Assert.assertTrue(Boolean.getBoolean(STANDALONE_MODE_PROPERTY_NAME));
        } else {
            Assert.assertFalse(Boolean.getBoolean(STANDALONE_MODE_PROPERTY_NAME));
        }
        
        Assert.assertEquals(standaloneMode, Boolean.getBoolean(STANDALONE_MODE_PROPERTY_NAME));
        
    }
    
    @Test
    public void testPreferHostModeConstants() {
        
        System.out.printf("System property \"%s\" = %s \n", "nacos.preferrHostnameOverIp", preferHostMode);
        
        if ("true".equalsIgnoreCase(System.getProperty("nacos.preferHostnameOverIp"))) {
            Assert.assertTrue(Boolean.getBoolean(PREFER_HOSTNAME_OVER_IP_PROPERTY_NAME));
        } else {
            Assert.assertFalse(Boolean.getBoolean(PREFER_HOSTNAME_OVER_IP_PROPERTY_NAME));
        }
        
        Assert.assertEquals(preferHostMode, Boolean.getBoolean(PREFER_HOSTNAME_OVER_IP_PROPERTY_NAME));
        
    }
    
    @Test
    public void testReadClusterConf() throws IOException {
        FileUtils.forceMkdir(new File(EnvUtil.getConfPath()));
        
        String lineSeparator = System.getProperty("line.separator");
        
        /*
         * #it is ip
         * #example
         * 192.168.1.1:8848
         */
        EnvUtil.writeClusterConf("#it is ip" + lineSeparator + "#example" + lineSeparator + "192.168.1.1:8848");
        Assert.assertEquals(EnvUtil.readClusterConf().get(0), "192.168.1.1:8848");
        
        /*
         * #it is ip
         *   #example
         *   # 192.168.1.1:8848
         *   192.168.1.2:8848 # Instance A
         */
        EnvUtil.writeClusterConf(
                "#it is ip" + lineSeparator + "  #example" + lineSeparator + "  # 192.168.1.1:8848" + lineSeparator
                        + "  192.168.1.2:8848 # Instance A  " + lineSeparator + "192.168.1.3#:8848");
        List<String> instanceList = EnvUtil.readClusterConf();
        Assert.assertEquals(instanceList.get(0), "192.168.1.2:8848");
        Assert.assertEquals(instanceList.get(1), "192.168.1.3");
    }
    
}
