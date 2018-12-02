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
package com.alibaba.nacos.naming.misc;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class SwitchTest {

    @Before
    public void before() {

        SwitchDomain domain = new SwitchDomain();
        Switch.setDom(domain);
    }

    @Test
    public void udpateSwitch() {

        Switch.setCheckTimes(5);
        Assert.assertEquals(5, Switch.getCheckTimes());

        Switch.setAdWeight("1.1.1.1", 20);
        Assert.assertEquals(20, Switch.getAdWeight("1.1.1.1").intValue());

        Switch.setCacheMillis("nacos.domain.1", 5000);
        Assert.assertEquals(5000, Switch.getCacheMillis("nacos.domain.1"));

        Switch.setAllDomNameCache(false);
        Assert.assertTrue(!Switch.isAllDomNameCache());

        Switch.setClientBeatInterval(1000L);
        Assert.assertEquals(1000L, Switch.getClientBeatInterval());

        Switch.setDisableAddIP(true);
        Assert.assertTrue(Switch.getDisableAddIP());

        Switch.setDistroEnabled(true);
        Assert.assertTrue(Switch.isDistroEnabled());

    }
}
