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

package com.alibaba.nacos.core.cluster.lookup;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.cluster.MemberLookup;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.file.WatchFileCenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodName.class)
class LookupFactoryTest {
    
    private static final String LOOKUP_MODE_TYPE = "nacos.core.member.lookup.type";
    
    MockEnvironment mockEnvironment;
    
    @Mock
    private ServerMemberManager memberManager;
    
    private MemberLookup memberLookup;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        mockEnvironment = new MockEnvironment();
        EnvUtil.setEnvironment(mockEnvironment);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        WatchFileCenter.deregisterAllWatcher(EnvUtil.getConfPath());
        memberManager.shutdown();
    }
    
    /**
     * createLookUpStandalone MemberLookup.
     *
     * @throws NacosException NacosException
     */
    @Test
    void createLookUpStandaloneMemberLookup() throws NacosException {
        EnvUtil.setIsStandalone(true);
        memberLookup = LookupFactory.createLookUp(memberManager);
        assertEquals(StandaloneMemberLookup.class, memberLookup.getClass());
    }
    
    @Test
    void createLookUpFileConfigMemberLookup() throws Exception {
        EnvUtil.setIsStandalone(false);
        mockEnvironment.setProperty(LOOKUP_MODE_TYPE, "file");
        memberLookup = LookupFactory.createLookUp(memberManager);
        assertEquals(FileConfigMemberLookup.class, memberLookup.getClass());
    }
    
    @Test
    void createLookUpAddressServerMemberLookup() throws Exception {
        EnvUtil.setIsStandalone(false);
        mockEnvironment.setProperty(LOOKUP_MODE_TYPE, "address-server");
        memberLookup = LookupFactory.createLookUp(memberManager);
        assertEquals(AddressServerMemberLookup.class, memberLookup.getClass());
    }
    
    @Test
    void testSwitchLookup() throws Exception {
        EnvUtil.setIsStandalone(false);
        createLookUpFileConfigMemberLookup();
        EnvUtil.setIsStandalone(false);
        String name1 = "file";
        MemberLookup memberLookup = LookupFactory.switchLookup(name1, memberManager);
        assertEquals(FileConfigMemberLookup.class, memberLookup.getClass());
        
        createLookUpAddressServerMemberLookup();
        String name2 = "address-server";
        memberLookup = LookupFactory.switchLookup(name2, memberManager);
        assertEquals(AddressServerMemberLookup.class, memberLookup.getClass());
        
        createLookUpStandaloneMemberLookup();
        String name3 = "address-server";
        memberLookup = LookupFactory.switchLookup(name3, memberManager);
        assertEquals(StandaloneMemberLookup.class, memberLookup.getClass());
    }
}