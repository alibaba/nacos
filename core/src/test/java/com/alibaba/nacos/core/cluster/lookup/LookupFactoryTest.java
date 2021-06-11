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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.MemberLookup;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.StandardEnvironment;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.Objects;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LookupFactoryTest extends TestCase {
    
    @Mock
    private ServletContext servletContext;
    
    private static final String LOOKUP_MODE_TYPE = "nacos.core.member.lookup.type";
    
    private ServerMemberManager memberManager;
    
    private MemberLookup memberLookup;
    
    @Before
    public void setUp() throws Exception {
        when(servletContext.getContextPath()).thenReturn("");
        EnvUtil.setEnvironment(new StandardEnvironment());
        memberManager = new ServerMemberManager(servletContext);
    }
    
    @Test
    public void testCreateLookUp() throws NacosException {
        memberLookup = LookupFactory.createLookUp(memberManager);
        if (EnvUtil.getStandaloneMode()) {
            assertEquals(memberLookup.getClass(), StandaloneMemberLookup.class);
        } else {
            String lookupType = EnvUtil.getProperty(LOOKUP_MODE_TYPE);
            if (StringUtils.isNotBlank(lookupType)) {
                LookupFactory.LookupType type = LookupFactory.LookupType.sourceOf(lookupType);
                if (Objects.nonNull(type)) {
                    if (LookupFactory.LookupType.FILE_CONFIG.equals(type)) {
                        assertEquals(memberLookup.getClass(), FileConfigMemberLookup.class);
                    }
                    if (LookupFactory.LookupType.ADDRESS_SERVER.equals(type)) {
                        assertEquals(memberLookup.getClass(), AddressServerMemberLookup.class);
                    }
                } else {
                    File file = new File(EnvUtil.getClusterConfFilePath());
                    if (file.exists() || StringUtils.isNotBlank(EnvUtil.getMemberList())) {
                        assertEquals(memberLookup.getClass(), FileConfigMemberLookup.class);
                    } else {
                        assertEquals(memberLookup.getClass(), AddressServerMemberLookup.class);
                    }
                }
            } else {
                File file = new File(EnvUtil.getClusterConfFilePath());
                if (file.exists() || StringUtils.isNotBlank(EnvUtil.getMemberList())) {
                    assertEquals(memberLookup.getClass(), FileConfigMemberLookup.class);
                } else {
                    assertEquals(memberLookup.getClass(), AddressServerMemberLookup.class);
                }
            }
        }
    }
    
    @Test
    public void testSwitchLookup() throws NacosException {
        String name1 = "file";
        MemberLookup memberLookup = LookupFactory.switchLookup(name1, memberManager);
        assertEquals(memberLookup.getClass(), FileConfigMemberLookup.class);
        String name2 = "address-server";
        memberLookup = LookupFactory.switchLookup(name2, memberManager);
        assertEquals(memberLookup.getClass(), AddressServerMemberLookup.class);
    }
}