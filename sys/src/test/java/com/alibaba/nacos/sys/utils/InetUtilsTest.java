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

package com.alibaba.nacos.sys.utils;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.sys.env.Constants.NACOS_SERVER_IP;

public class InetUtilsTest {
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    public void testRefreshIp() throws InterruptedException {
        System.setProperty(NACOS_SERVER_IP, "1.1.1.1");
        System.setProperty(Constants.AUTO_REFRESH_TIME, "100");
        String selfIP = InetUtils.getSelfIP();
        Assert.assertTrue(StringUtils.equalsIgnoreCase(selfIP, "1.1.1.1"));
        
        System.setProperty(NACOS_SERVER_IP, "1.1.1.2");
        TimeUnit.MILLISECONDS.sleep(500L);
        
        selfIP = InetUtils.getSelfIP();
        Assert.assertTrue(StringUtils.equalsIgnoreCase(selfIP, "1.1.1.2"));
        
    }
    
    @After
    public void tearDown() {
        System.clearProperty(NACOS_SERVER_IP);
        System.clearProperty(Constants.AUTO_REFRESH_TIME);
    }
    
}