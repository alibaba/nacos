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

package com.alibaba.nacos.test.config;

import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.config.server.model.event.RaftDbErrorRecoverEvent;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.embedded.EmbeddedConfigInfoPersistServiceImpl;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftConstants;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.persistence.model.event.RaftDbErrorEvent;
import com.alibaba.nacos.sys.utils.InetUtils;
import com.alibaba.nacos.test.base.BaseClusterTest;
import com.alibaba.nacos.test.base.ConfigCleanUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
@TestMethodOrder(MethodName.class)
// todo the suffix is _DITCase, the case will be skipped by default
class ConfigDerbyRaft_DITCase extends BaseClusterTest {
    
    @BeforeAll
    static void beforeClass() {
        ConfigCleanUtils.changeToNewTestNacosHome(ConfigDerbyRaft_DITCase.class.getSimpleName());
    }
    
    @Test
    void test_a_publish_config() throws Exception {
        boolean result = iconfig7.publishConfig("raft_test", "cluster_test_1", "this.is.raft_cluster=lessspring_7");
        assertTrue(result);
        
        ThreadUtils.sleep(5000);
        
        ConfigurableApplicationContext context7 = applications.get("8847");
        ConfigurableApplicationContext context8 = applications.get("8848");
        ConfigurableApplicationContext context9 = applications.get("8849");
        
        ConfigInfoPersistService operate7 = context7.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        ConfigInfoPersistService operate8 = context8.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        ConfigInfoPersistService operate9 = context9.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        
        String s7 = operate7.findConfigInfo("raft_test", "cluster_test_1", "").getContent();
        String s8 = operate8.findConfigInfo("raft_test", "cluster_test_1", "").getContent();
        String s9 = operate9.findConfigInfo("raft_test", "cluster_test_1", "").getContent();
        
        assertArrayEquals(new String[] {s7, s8, s9}, new String[] {"this.is.raft_cluster=lessspring_7", "this.is.raft_cluster=lessspring_7",
                "this.is.raft_cluster=lessspring_7"}, "The three nodes must have consistent data");
    }
    
    @Test
    void test_b_publish_config() throws Exception {
        ThreadUtils.sleep(5000);
        
        boolean result = iconfig8.publishConfig("raft_test", "cluster_test_2", "this.is.raft_cluster=lessspring_8");
        assertTrue(result);
        
        ThreadUtils.sleep(5000);
        
        ConfigurableApplicationContext context7 = applications.get("8847");
        ConfigurableApplicationContext context8 = applications.get("8848");
        ConfigurableApplicationContext context9 = applications.get("8849");
        
        ConfigInfoPersistService operate7 = context7.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        ConfigInfoPersistService operate8 = context8.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        ConfigInfoPersistService operate9 = context9.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        
        String s7 = operate7.findConfigInfo("raft_test", "cluster_test_2", "").getContent();
        String s8 = operate8.findConfigInfo("raft_test", "cluster_test_2", "").getContent();
        String s9 = operate9.findConfigInfo("raft_test", "cluster_test_2", "").getContent();
        
        assertArrayEquals(new String[] {s7, s8, s9}, new String[] {"this.is.raft_cluster=lessspring_8", "this.is.raft_cluster=lessspring_8",
                "this.is.raft_cluster=lessspring_8"}, "The three nodes must have consistent data");
    }
    
    @Test
    void test_c_publish_config() throws Exception {
        ThreadUtils.sleep(5000);
        boolean result = iconfig9.publishConfig("raft_test", "cluster_test_2", "this.is.raft_cluster=lessspring_9");
        assertTrue(result);
        
        ThreadUtils.sleep(5000);
        
        ConfigurableApplicationContext context7 = applications.get("8847");
        ConfigurableApplicationContext context8 = applications.get("8848");
        ConfigurableApplicationContext context9 = applications.get("8849");
        
        ConfigInfoPersistService operate7 = context7.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        ConfigInfoPersistService operate8 = context8.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        ConfigInfoPersistService operate9 = context9.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        
        String s7 = operate7.findConfigInfo("raft_test", "cluster_test_2", "").getContent();
        String s8 = operate8.findConfigInfo("raft_test", "cluster_test_2", "").getContent();
        String s9 = operate9.findConfigInfo("raft_test", "cluster_test_2", "").getContent();
        
        assertArrayEquals(new String[] {s7, s8, s9}, new String[] {"this.is.raft_cluster=lessspring_9", "this.is.raft_cluster=lessspring_9",
                "this.is.raft_cluster=lessspring_9"}, "The three nodes must have consistent data");
    }
    
    @Test
    void test_d_modify_config() throws Exception {
        boolean result = iconfig7.publishConfig("raft_test", "cluster_test_1", "this.is.raft_cluster=lessspring_7_it_is_for_modify");
        assertTrue(result);
        
        ThreadUtils.sleep(5000);
        
        ConfigurableApplicationContext context7 = applications.get("8847");
        ConfigurableApplicationContext context8 = applications.get("8848");
        ConfigurableApplicationContext context9 = applications.get("8849");
        
        ConfigInfoPersistService operate7 = context7.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        ConfigInfoPersistService operate8 = context8.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        ConfigInfoPersistService operate9 = context9.getBean(EmbeddedConfigInfoPersistServiceImpl.class);
        
        String s7 = operate7.findConfigInfo("raft_test", "cluster_test_1", "").getContent();
        String s8 = operate8.findConfigInfo("raft_test", "cluster_test_1", "").getContent();
        String s9 = operate9.findConfigInfo("raft_test", "cluster_test_1", "").getContent();
        
        assertArrayEquals(new String[] {s7, s8, s9},
                new String[] {"this.is.raft_cluster=lessspring_7_it_is_for_modify", "this.is.raft_cluster=lessspring_7_it_is_for_modify",
                        "this.is.raft_cluster=lessspring_7_it_is_for_modify"}, "The three nodes must have consistent data");
    }
    
    @Test
    void test_l_client_operation() throws Exception {
        final String dataId = "test_l_client_operation";
        final String groupId = "test_l_client_operation";
        String content = "test_l_client_operation" + System.currentTimeMillis();
        
        // publish by 8847
        boolean result = iconfig7.publishConfig(dataId, groupId, content);
        assertTrue(result);
        ThreadUtils.sleep(5000);
        
        String v1_7 = iconfig7.getConfig(dataId, groupId, 5000L);
        String v1_8 = iconfig8.getConfig(dataId, groupId, 5000L);
        String v1_9 = iconfig9.getConfig(dataId, groupId, 5000L);
        
        assertEquals(content, v1_7);
        assertEquals(content, v1_8);
        assertEquals(content, v1_9);
        
        // publish by 8848
        content = "test_l_client_operation" + System.currentTimeMillis();
        result = iconfig8.publishConfig(dataId, groupId, content);
        assertTrue(result);
        ThreadUtils.sleep(5000);
        
        String v2_7 = iconfig7.getConfig(dataId, groupId, 5000L);
        String v2_8 = iconfig8.getConfig(dataId, groupId, 5000L);
        String v2_9 = iconfig9.getConfig(dataId, groupId, 5000L);
        
        assertEquals(content, v2_7);
        assertEquals(content, v2_8);
        assertEquals(content, v2_9);
        
        // publish by 8849
        content = "test_l_client_operation" + System.currentTimeMillis();
        result = iconfig9.publishConfig(dataId, groupId, content);
        assertTrue(result);
        ThreadUtils.sleep(5000);
        
        String v3_7 = iconfig7.getConfig(dataId, groupId, 5000L);
        String v3_8 = iconfig8.getConfig(dataId, groupId, 5000L);
        String v3_9 = iconfig9.getConfig(dataId, groupId, 5000L);
        
        assertEquals(content, v3_7);
        assertEquals(content, v3_8);
        assertEquals(content, v3_9);
        
        // delete by 8849
        result = iconfig9.removeConfig(dataId, groupId);
        assertTrue(result);
        ThreadUtils.sleep(5000);
        
        String v4_7 = iconfig7.getConfig(dataId, groupId, 5000L);
        String v4_8 = iconfig8.getConfig(dataId, groupId, 5000L);
        String v4_9 = iconfig9.getConfig(dataId, groupId, 5000L);
        
        assertNull(v4_7);
        assertNull(v4_8);
        assertNull(v4_9);
    }
    
    @Test
    void test_k_config_listener() throws Exception {
        String dataId = "test_h_config_listener";
        String group = "test_h_config_listener";
        String content = "test_h_config_listener";
        CountDownLatch[] latch = new CountDownLatch[] {new CountDownLatch(1), new CountDownLatch(0)};
        AtomicReference<String> r = new AtomicReference<>();
        AtomicInteger i = new AtomicInteger(0);
        iconfig7.addListener(dataId, group, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println(configInfo);
                r.set(configInfo);
                latch[i.getAndIncrement()].countDown();
            }
        });
        
        iconfig7.publishConfig(dataId, group, content);
        
        ThreadUtils.sleep(10_000L);
        latch[0].await(10_000L, TimeUnit.MILLISECONDS);
        assertEquals(content, r.get());
        assertEquals(content, iconfig7.getConfig(dataId, group, 2_000L));
        
        content = content + System.currentTimeMillis();
        iconfig7.publishConfig(dataId, group, content);
        
        ThreadUtils.sleep(10_000L);
        latch[1].await(10_000L, TimeUnit.MILLISECONDS);
        assertEquals(content, r.get());
        assertEquals(content, iconfig7.getConfig(dataId, group, 2_000L));
    }
    
    @Test
    void test_e_derby_ops() throws Exception {
        String url = "http://127.0.0.1:8848/nacos/v1/cs/ops/derby";
        Query query = Query.newInstance().addParam("sql", "select * from users");
        RestResult<List<Map<String, Object>>> result = NACOS_REST_TEMPLATE.get(url, Header.EMPTY, query,
                new GenericType<RestResult<List<Map<String, Object>>>>() {
                }.getType());
        System.out.println(result.getData());
        assertTrue(result.ok());
        List<Map<String, Object>> list = result.getData();
        assertEquals(1, list.size());
        assertEquals("nacos", list.get(0).get("USERNAME"));
    }
    
    @Test
    void test_g_derby_ops_no_select() throws Exception {
        String url = "http://127.0.0.1:8848/nacos/v1/cs/ops/derby";
        Query query = Query.newInstance().addParam("sql", "update users set username='nacos'");
        RestResult<Object> result = NACOS_REST_TEMPLATE.get(url, Header.EMPTY, query, new GenericType<RestResult<Object>>() {
        }.getType());
        System.out.println(result);
        assertFalse(result.ok());
        assertEquals("Only query statements are allowed to be executed", result.getMessage());
    }
    
    @Test
    void test_h_derby_has_error() throws Exception {
        
        ThreadUtils.sleep(5000);
        
        boolean result = iconfig7.publishConfig("raft_test_raft_error", "cluster_test_1", "this.is.raft_cluster=lessspring_7");
        assertTrue(result);
        
        NotifyCenter.registerToPublisher(RaftDbErrorRecoverEvent.class, 8);
        
        CountDownLatch latch1 = new CountDownLatch(1);
        NotifyCenter.registerSubscriber(new Subscriber<RaftDbErrorEvent>() {
            @Override
            public void onEvent(RaftDbErrorEvent event) {
                latch1.countDown();
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return RaftDbErrorEvent.class;
            }
        });
        NotifyCenter.publishEvent(new RaftDbErrorEvent());
        latch1.await(10_000L, TimeUnit.MILLISECONDS);
        
        result = iconfig7.publishConfig("raft_test_raft_error", "cluster_test_1", "this.is.raft_cluster=lessspring_7");
        assertFalse(result);
        
        CountDownLatch latch2 = new CountDownLatch(1);
        NotifyCenter.registerSubscriber(new Subscriber<RaftDbErrorRecoverEvent>() {
            
            @Override
            public void onEvent(RaftDbErrorRecoverEvent event) {
                latch2.countDown();
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return RaftDbErrorRecoverEvent.class;
            }
        });
        NotifyCenter.publishEvent(new RaftDbErrorRecoverEvent());
        latch2.await(10_000L, TimeUnit.MILLISECONDS);
        
        result = iconfig7.publishConfig("raft_test_raft_error", "cluster_test_1", "this.is.raft_cluster=lessspring_7");
        assertTrue(result);
    }
    
    @Test
    void test_f_id_generator_leader_transfer() throws Exception {
        ConfigurableApplicationContext context7 = applications.get("8847");
        ConfigurableApplicationContext context8 = applications.get("8848");
        ConfigurableApplicationContext context9 = applications.get("8849");
        IdGeneratorManager manager7 = context7.getBean(IdGeneratorManager.class);
        IdGeneratorManager manager8 = context8.getBean(IdGeneratorManager.class);
        IdGeneratorManager manager9 = context9.getBean(IdGeneratorManager.class);
        
        CPProtocol protocol7 = context7.getBean(CPProtocol.class);
        CPProtocol protocol8 = context8.getBean(CPProtocol.class);
        CPProtocol protocol9 = context9.getBean(CPProtocol.class);
        
        final String configGroup = PersistenceConstant.CONFIG_MODEL_RAFT_GROUP;
        long preId = -1L;
        long currentId = -1L;
        
        if (protocol7.isLeader(configGroup)) {
            preId = manager7.nextId(CONFIG_INFO_ID);
        }
        if (protocol8.isLeader(configGroup)) {
            preId = manager8.nextId(CONFIG_INFO_ID);
        }
        if (protocol9.isLeader(configGroup)) {
            preId = manager9.nextId(CONFIG_INFO_ID);
        }
        
        // transfer leader to ip:8807
        
        Map<String, String> transfer = new HashMap<>();
        transfer.put(JRaftConstants.TRANSFER_LEADER, InetUtils.getSelfIP() + ":9847");
        RestResult<String> result = protocol7.execute(transfer);
        System.out.println(result);
        assertTrue(result.ok());
        
        TimeUnit.SECONDS.sleep(2);
        
        assertTrue(protocol7.isLeader(configGroup));
        currentId = manager7.nextId(CONFIG_INFO_ID);
        assertNotEquals(preId, currentId);
        preId = currentId;
        
        // transfer leader to ip:8808
        
        transfer = new HashMap<>();
        transfer.put(JRaftConstants.TRANSFER_LEADER, InetUtils.getSelfIP() + ":9848");
        result = protocol8.execute(transfer);
        System.out.println(result);
        assertTrue(result.ok());
        
        TimeUnit.SECONDS.sleep(2);
        
        assertTrue(protocol8.isLeader(configGroup));
        currentId = manager8.nextId(CONFIG_INFO_ID);
        assertNotEquals(preId, currentId);
        preId = currentId;
        
        // transfer leader to ip:8809
        
        transfer = new HashMap<>();
        transfer.put(JRaftConstants.TRANSFER_LEADER, InetUtils.getSelfIP() + ":9849");
        result = protocol9.execute(transfer);
        System.out.println(result);
        assertTrue(result.ok());
        
        TimeUnit.SECONDS.sleep(2);
        
        assertTrue(protocol9.isLeader(configGroup));
        currentId = manager9.nextId(CONFIG_INFO_ID);
        assertNotEquals(preId, currentId);
        
    }
    
}
