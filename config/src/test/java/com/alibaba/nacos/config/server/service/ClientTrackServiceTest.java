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
package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.utils.GroupKey2;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class ClientTrackServiceTest {

    @Before
    public void before() {
        ClientTrackService.clientRecords.clear();
    }

    @Test
    public void test_trackClientMd5() {
        String clientIp = "1.1.1.1";
        String dataId = "com.taobao.session.xml";
        String group = "online";
        String groupKey = GroupKey2.getKey(dataId, group);
        String md5 = "xxxxxxxxxxxxx";

        ConfigCacheService.updateMd5(groupKey, md5, System.currentTimeMillis());

        ClientTrackService.trackClientMd5(clientIp, groupKey, md5);
        ClientTrackService.trackClientMd5(clientIp, groupKey, md5);

        Assert.assertEquals(true, ClientTrackService.isClientUptodate(clientIp).get(groupKey));
        Assert.assertEquals(1, ClientTrackService.subscribeClientCount());
        Assert.assertEquals(1, ClientTrackService.subscriberCount());

        //服务端数据更新
        ConfigCacheService.updateMd5(groupKey, md5 + "111", System.currentTimeMillis());
        Assert.assertEquals(false, ClientTrackService.isClientUptodate(clientIp).get(groupKey));
    }

}
