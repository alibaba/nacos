/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.cluster.lookup;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;

/**
 * {@link FileConfigMemberLookup} unit test.
 *
 * @author chenglu
 * @date 2021-07-08 22:23
 */
@RunWith(MockitoJUnitRunner.class)
public class FileConfigMemberLookupTest {
    
    private FileConfigMemberLookup fileConfigMemberLookup;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Before
    public void setUp() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        fileConfigMemberLookup = new FileConfigMemberLookup();
        fileConfigMemberLookup.injectMemberManager(memberManager);
        fileConfigMemberLookup.start();
    }
    
    @After
    public void tearDown() throws NacosException {
        fileConfigMemberLookup.destroy();
    }
    
    @Test
    public void testAfterLookup() {
        try {
            fileConfigMemberLookup.afterLookup(Collections.singletonList(new Member()));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testUseAddressServer() {
        Assert.assertFalse(fileConfigMemberLookup.useAddressServer());
    }
}
