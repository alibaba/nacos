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

package com.alibaba.nacos.test.core.cluster;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberLookup;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.lookup.AddressServerMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.FileConfigMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.LookupFactory;
import com.alibaba.nacos.core.cluster.lookup.StandaloneMemberLookup;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test case for Member Lookup functionality, validating different lookup strategies: file configuration,
 * standalone mode, and address server lookup.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@TestMethodOrder(MethodName.class)
class MemberLookupCoreITCase {
    
    private final String path = Paths.get(System.getProperty("user.home"), "/member_look").toString();
    
    private final String name = "cluster.conf";
    
    ServerMemberManager memberManager;
    
    @BeforeEach
    void before() throws Exception {
        System.setProperty("nacos.home", path);
        EnvUtil.setEnvironment(new StandardEnvironment());
        EnvUtil.setIsStandalone(false);
        System.out.println(EnvUtil.getStandaloneMode());
        
        System.out.println(Arrays.toString(LookupFactory.LookupType.values()));
        DiskUtils.forceMkdir(path);
        DiskUtils.forceMkdir(Paths.get(path, "conf").toString());
        File file = Paths.get(path, "conf", name).toFile();
        DiskUtils.touch(file);
        String ip = InetUtils.getSelfIP();
        DiskUtils.writeFile(file, (ip + ":8848," + ip + ":8847," + ip + ":8849").getBytes(StandardCharsets.UTF_8),
                false);
        
        try {
            memberManager = new ServerMemberManager(new MockServletContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @AfterEach
    void after() throws Exception {
        try {
            memberManager.shutdown();
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
        DiskUtils.deleteDirectory(path);
    }
    
    @Test
    void testLookupFileConfig() throws Exception {
        try {
            LookupFactory.createLookUp(memberManager);
        } catch (Throwable ignore) {
        }
        MemberLookup lookup = LookupFactory.getLookUp();
        System.out.println(lookup);
        assertInstanceOf(FileConfigMemberLookup.class, lookup);
        func(lookup);
    }
    
    @Test
    void testLookupStandalone() throws Exception {
        EnvUtil.setIsStandalone(true);
        try {
            LookupFactory.createLookUp(memberManager);
        } catch (Throwable ignore) {
        
        } finally {
            EnvUtil.setIsStandalone(false);
        }
        MemberLookup lookup = LookupFactory.getLookUp();
        System.out.println(lookup);
        assertInstanceOf(StandaloneMemberLookup.class, lookup);
    }
    
    @Test
    void testLookupAddressServer() throws Exception {
        EnvUtil.setIsStandalone(false);
        System.out.println(EnvUtil.getClusterConfFilePath());
        DiskUtils.deleteFile(Paths.get(path, "conf").toString(), "cluster.conf");
        System.out.println(new File(EnvUtil.getClusterConfFilePath()).exists());
        try {
            LookupFactory.createLookUp(memberManager);
        } catch (Throwable ignore) {
        }
        MemberLookup lookup = LookupFactory.getLookUp();
        System.out.println(lookup);
        assertInstanceOf(AddressServerMemberLookup.class, lookup);
        try {
            func(lookup);
        } catch (NacosException e) {
            System.out.println(e.getErrMsg());
            assertTrue(StringUtils.containsIgnoreCase(e.getErrMsg(), "jmenv.tbsite.net"));
        }
    }
    
    private void func(MemberLookup lookup) throws Exception {
        func(lookup, 3);
    }
    
    private void func(MemberLookup lookup, int expectSize) throws Exception {
        lookup.start();
        Map<String, Member> tmp = memberManager.getServerList();
        System.out.println(lookup + " : " + tmp);
        assertEquals(expectSize, tmp.size());
    }
    
}
