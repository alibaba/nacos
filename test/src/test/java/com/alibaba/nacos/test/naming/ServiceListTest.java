package com.alibaba.nacos.test.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.NamingApp;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author dungu.zpf
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ServiceListTest {

    private NamingService naming;

    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception{
        if (naming == null) {
            TimeUnit.SECONDS.sleep(10);
            naming = NamingFactory.createNamingService("127.0.0.1"+":"+port);
        }
    }

    @Test
    public void serviceList() throws NacosException {
        naming.getServicesOfServer(1, 10);
    }

    @Test
    public void getSubscribeServices() throws NacosException {

        ListView<String> listView = naming.getServicesOfServer(1, 10);
        if (listView != null && listView.getCount() > 0) {
            naming.getAllInstances(listView.getData().get(0));
        }
        List<ServiceInfo> serviceInfoList = naming.getSubscribeServices();

        System.out.println(serviceInfoList);
    }
}
