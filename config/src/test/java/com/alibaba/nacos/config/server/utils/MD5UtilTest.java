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
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MD5UtilTest {

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    private String content = "content";

    @Before
    public void init() {

        ConfigCacheService.dump("test", "DEFAULT_GROUP", "", content, System.currentTimeMillis(), "text");
    }

    @Test
    public void publicNameSpaceTest() {
        Map<String, String> clientMd5Map = new HashMap<>();
        String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
        clientMd5Map.put("test+DEFAULT_GROUP+public", md5);
        clientMd5Map.put("test+DEFAULT_GROUP", md5);
        List<String> changedGroups = MD5Util.compareMd5(request, response, clientMd5Map);

        Assert.assertEquals(changedGroups.size(), 0);

    }
}
