package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.PushService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @description:
 * @author: codeimport
 * @create: 2019/12/6 18:30
 **/
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class PushServiceTest extends BaseTest {
    @InjectMocks
    private PushService pushService;

    @Before
    public void before() {
        super.before();
    }

    @Test
    public void testGetClientsFuzzy() throws Exception {

        String namespaceId = "public";
        String clusters = "DEFAULT";
        String agent = "Nacos-Java-Client:v1.1.4";
        String clientIp = "localhost";
        String app = "nacos";
        int udpPort = 10000;

        String helloServiceName = "helloGroupName@@helloServiceName";
        int helloUdpPort = 10000;

        String testServiceName = "testGroupName@@testServiceName";
        int testUdpPort = 10001;

        pushService.addClient(namespaceId
            , helloServiceName
            , clusters
            , agent
            , new InetSocketAddress(clientIp, helloUdpPort)
            , null
            , namespaceId
            , app);

        pushService.addClient(namespaceId
            , testServiceName
            , clusters
            , agent
            , new InetSocketAddress(clientIp, testUdpPort)
            , null
            , namespaceId
            , app);

        List<Subscriber> fuzzylist =  pushService.getClientsFuzzy("hello@@hello","public");
        Assert.assertEquals(fuzzylist.size(),1);
        Assert.assertEquals(fuzzylist.get(0).getServiceName(),"helloGroupName@@helloServiceName");

        List<Subscriber> list =  pushService.getClientsFuzzy("helloGroupName@@helloServiceName","public");
        Assert.assertEquals(list.size(),1);
        Assert.assertEquals(list.get(0).getServiceName(),"helloGroupName@@helloServiceName");

        List<Subscriber> noDataList =  pushService.getClientsFuzzy("badGroupName@@badServiceName","public");
        Assert.assertEquals(noDataList.size(),0);
    }
}
