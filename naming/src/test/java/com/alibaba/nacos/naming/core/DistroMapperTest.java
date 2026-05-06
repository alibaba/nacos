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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashSet;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DistroMapperTest {
    
    private DistroMapper distroMapper;
    
    @Mock
    private ServerMemberManager memberManager;
    
    private SwitchDomain switchDomain;
    
    private String serviceName = "com.taobao.service";
    
    private String ip1 = "1.1.1.1";
    
    private String ip2 = "2.2.2.2";
    
    private String ip3 = "3.3.3.3";
    
    private String ip4 = "4.4.4.4";
    
    private int port = 8848;
    
    @BeforeEach
    void setUp() {
        ConcurrentSkipListMap<String, Member> serverList = new ConcurrentSkipListMap<>();
        EnvUtil.setEnvironment(new StandardEnvironment());
        EnvUtil.setIsStandalone(true);
        serverList.put(ip1, Member.builder().ip(ip1).port(port).build());
        serverList.put(ip2, Member.builder().ip(ip2).port(port).build());
        serverList.put(ip3, Member.builder().ip(ip3).port(port).build());
        EnvUtil.setLocalAddress(ip4);
        serverList.put(EnvUtil.getLocalAddress(), Member.builder().ip(EnvUtil.getLocalAddress()).port(port).build());
        HashSet<Member> set = new HashSet<>(serverList.values());
        switchDomain = new SwitchDomain();
        distroMapper = new DistroMapper(memberManager, switchDomain);
    }
    
    @Test
    void testResponsible() {
        assertTrue(distroMapper.responsible(serviceName));
    }
    
    @Test
    void testMapSrv() {
        String server = distroMapper.mapSrv(serviceName);
        assertEquals(server, ip4);
    }
}
