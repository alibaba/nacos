package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.boot.SpringContext;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.HealthCheckProcessorDelegate;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.CoreMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * @author jifengnan 2019-04-29
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class ClusterControllerTest extends BaseTest {
    @InjectMocks
    private ClusterController clusterController;

    private MockMvc mockmvc;

    @Before
    public void before() {
        super.before();
        mockmvc = MockMvcBuilders.standaloneSetup(clusterController).build();
    }

    @Test
    public void testUpdate() throws Exception {
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId("test-namespace");
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(service);

        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster")
                .param("clusterName", TEST_CLUSTER_NAME)
                .param("serviceName", TEST_SERVICE_NAME)
                .param("healthChecker", "{\"type\":\"HTTP\"}")
                .param("checkPort", "1")
                .param("useInstancePort4Check", "true");
        Assert.assertEquals("ok", mockmvc.perform(builder).andReturn().getResponse().getContentAsString());

        Cluster expectedCluster = new Cluster(TEST_CLUSTER_NAME, service);
        Cluster actualCluster = service.getClusterMap().get(TEST_CLUSTER_NAME);

        Assert.assertEquals(expectedCluster, actualCluster);
        Assert.assertEquals(1, actualCluster.getDefCkport());
        Assert.assertTrue(actualCluster.isUseIPPort4Check());
    }

    @Test
    public void testUpdateNoService() throws Exception {
        expectedException.expectCause(isA(NacosException.class));
        expectedException.expectMessage("service not found:test-service-not-found");
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster")
                .param("clusterName", TEST_CLUSTER_NAME)
                .param("serviceName", "test-service-not-found")
                .param("healthChecker", "{\"type\":\"HTTP\"}")
                .param("checkPort", "1")
                .param("useInstancePort4Check", "true");
        mockmvc.perform(builder);
    }

    @Test
    public void testUpdateInvalidType() throws Exception {
        expectedException.expectCause(isA(NacosException.class));
        expectedException.expectMessage("unknown health check type:{\"type\":\"123\"}");
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(service);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster")
                .param("clusterName", TEST_CLUSTER_NAME)
                .param("serviceName", TEST_SERVICE_NAME)
                .param("healthChecker", "{\"type\":\"123\"}")
                .param("checkPort", "1")
                .param("useInstancePort4Check", "true");
        mockmvc.perform(builder);
    }

}
