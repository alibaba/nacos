package com.alibaba.nacos.naming.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author caoyixiong
 * @Date: 2019/7/5
 * @Copyright (c) 2015, lianjia.com All Rights Reserved
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class ServiceControllerTest extends BaseTest {
    private static final String TEST_SERVICE_NAME_1 = "test-service-1";
    private static final String TEST_SERVICE_NAME_2 = "test-service-2";

    private static final String TEST_GROUP_NAME = "test-groupName";

    @InjectMocks
    private ServiceController serviceController;

    private MockMvc mockmvc;

    @Before
    public void before() {
        super.before();
        mockmvc = MockMvcBuilders.standaloneSetup(serviceController).build();
    }

    @Test
    public void getAllServiceInfo() throws Exception {
        Service service = new Service();
        service.setName(TEST_SERVICE_NAME);
        service.setGroupName(TEST_GROUP_NAME);

        Cluster cluster = new Cluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME, service);
        service.addCluster(cluster);

        Instance instance = new Instance();
        instance.setIp("10.10.10.10");
        instance.setPort(8888);
        instance.setWeight(2.0);
        instance.setServiceName(TEST_SERVICE_NAME);
        ArrayList<Instance> ipList = new ArrayList<>();
        ipList.add(instance);
        service.updateIPs(ipList, false);

        Service service1 = new Service();
        service1.setGroupName(TEST_GROUP_NAME);
        service1.setName(TEST_SERVICE_NAME_1);

        Cluster cluster1 = new Cluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME, service);
        service.addCluster(cluster1);

        Instance instance1 = new Instance();
        instance1.setIp("10.10.10.10");
        instance1.setPort(8888);
        instance1.setWeight(2.0);
        instance1.setServiceName(TEST_SERVICE_NAME);
        List<Instance> ipList1 = new ArrayList<>();
        ipList1.add(instance1);
        service1.updateIPs(ipList1, false);


        Service service2 = new Service();
        service2.setName(TEST_SERVICE_NAME_2);
        service2.setGroupName(TEST_GROUP_NAME);

        Cluster cluster2 = new Cluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME, service);
        service.addCluster(cluster2);

        Instance instance2 = new Instance();
        instance2.setIp("10.10.10.10");
        instance2.setPort(8888);
        instance2.setWeight(2.0);
        instance2.setServiceName(TEST_SERVICE_NAME);
        List<Instance> ipList2 = new ArrayList<>();
        ipList2.add(instance2);
        service2.updateIPs(ipList2, false);

        Map<String, Service> map = new HashMap<>();
        map.put(TEST_SERVICE_NAME, service);
        map.put(TEST_SERVICE_NAME_1, service1);
        map.put(TEST_SERVICE_NAME_2, service2);

        Mockito.when(serviceManager.chooseServiceMap(Constants.DEFAULT_NAMESPACE_ID)).thenReturn(map);

        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/service/getAll")
                .param("groupName", TEST_GROUP_NAME);

        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        JSONArray result = JSON.parseArray(actualValue);

        Assert.assertEquals(result.size(), 3);
    }
}
