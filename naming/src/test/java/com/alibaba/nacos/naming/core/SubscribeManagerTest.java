package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.PushService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Nicholas
 */
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
public class SubscribeManagerTest extends BaseTest {

    @Mock
    private SubscribeManager subscribeManager;

    @Mock
    private PushService pushService;

    @Mock
    private ServerMemberManager memberManager;

    @Before
    public void before() {
        super.before();
        subscribeManager = new SubscribeManager();
    }

    @Test
    public void getSubscribersWithFalse() {
        String serviceName = "test";
        String namespaceId = "public";
        boolean aggregation = Boolean.FALSE;
        try {
            List<Subscriber> clients = new ArrayList<Subscriber>();
            Subscriber subscriber = new Subscriber("127.0.0.1:8080", "test", "app", "127.0.0.1", namespaceId, serviceName);
            clients.add(subscriber);
            Mockito.when(pushService.getClients(Mockito.anyString(), Mockito.anyString())).thenReturn(clients);
            List<Subscriber> list = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);
            Assert.assertNotNull(list);
            Assert.assertEquals(1, list.size());
            Assert.assertEquals("public", list.get(0).getNamespaceId());
        } catch (Exception e) {

        }
    }

    @Test
    public void testGetSubscribersFuzzy() {
        String serviceName = "test";
        String namespaceId = "public";
        boolean aggregation = Boolean.TRUE;
        try {
            List<Subscriber> clients = new ArrayList<Subscriber>();
            Subscriber subscriber = new Subscriber("127.0.0.1:8080", "test", "app", "127.0.0.1", namespaceId, "testGroupName@@test_subscriber");
            clients.add(subscriber);
            Mockito.when(pushService.getClientsFuzzy(Mockito.anyString(), Mockito.anyString())).thenReturn(clients);
            List<Subscriber> list = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);
            Assert.assertNotNull(list);
            Assert.assertEquals(1, list.size());
            Assert.assertEquals("testGroupName@@test_subscriber", list.get(0).getServiceName());
        } catch (Exception e) {

        }
    }

    @Test
    public void getSubscribersWithTrue() {
        String serviceName = "test";
        String namespaceId = "public";
        boolean aggregation = Boolean.TRUE;
        try {
            List<Subscriber> clients = new ArrayList<Subscriber>();
            Subscriber subscriber = new Subscriber("127.0.0.1:8080", "test", "app", "127.0.0.1", namespaceId, serviceName);
            clients.add(subscriber);

            List<Member> healthyServers = new ArrayList<>();

            for (int i = 0; i <= 2; i++) {
                Member server = new Member();
                server.setIp("127.0.0.1");
                server.setPort(8080 + i);
                server.setState(NodeState.UP);
                server.setExtendVal(MemberMetaDataConstants.AD_WEIGHT, 10);
                server.setExtendVal(MemberMetaDataConstants.SITE_KEY, "site");
                server.setExtendVal(MemberMetaDataConstants.WEIGHT, 1);
                server.setExtendVal(MemberMetaDataConstants.RAFT_PORT, 8000 + i);
                healthyServers.add(server);
            }

            Mockito.when(memberManager.allMembers()).thenReturn(healthyServers);
            //Mockito.doReturn(3).when(serverListManager.getHealthyServers().size());
            List<Subscriber> list = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);
            Assert.assertNotNull(list);
            Assert.assertEquals(2, list.size());
            Assert.assertEquals("public", list.get(0).getNamespaceId());
        } catch (Exception e) {

        }
    }
}

