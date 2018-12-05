package com.alibaba.nacos.console.controller;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.naming.web.ApiCommands;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

import javax.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.any;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class HealthControllerTest {

    @InjectMocks
    private HealthController healthController;

    @Mock
    private PersistService persistService;

    @Mock
    private ApiCommands apiCommands;

    private MockMvc mockmvc;

    @Before
    public void setUp() {
        mockmvc = MockMvcBuilders.standaloneSetup(healthController).build();
    }

    @Test
    public void testLiveness() throws Exception {
        String url = "/v1/console/health/liveness";
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(url);
        Assert.assertEquals(200, mockmvc.perform(builder).andReturn().getResponse().getStatus());
    }

    @Test
    public void testReadiness() throws Exception {
        String url = "/v1/console/health/readiness";

        Mockito.when(persistService.configInfoCount(any(String.class))).thenReturn(0);
        Mockito.when(apiCommands.hello(any(HttpServletRequest.class))).thenReturn(new JSONObject());
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(url);
        Assert.assertEquals(200, mockmvc.perform(builder).andReturn().getResponse().getStatus());

        // Config and Naming are not in readiness
        Mockito.when(persistService.configInfoCount(any(String.class))).thenThrow(
            new RuntimeException("HealthControllerTest.testReadiness"));
        Mockito.when(apiCommands.hello(any(HttpServletRequest.class))).thenThrow(
            new RuntimeException("HealthControllerTest.testReadiness"));
        builder = MockMvcRequestBuilders.get(url);
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        Assert.assertEquals(500, response.getStatus());
        Assert.assertEquals("Config and Naming are not in readiness", response.getContentAsString());

        // Config is not in readiness
        Mockito.when(persistService.configInfoCount(any(String.class))).thenThrow(
            new RuntimeException("HealthControllerTest.testReadiness"));
        Mockito.when(apiCommands.hello(any(HttpServletRequest.class))).thenReturn(new JSONObject());
        response = mockmvc.perform(builder).andReturn().getResponse();
        Assert.assertEquals(500, response.getStatus());
        Assert.assertEquals("Config is not in readiness", response.getContentAsString());

        // Naming is not in readiness
        Mockito.when(persistService.configInfoCount(any(String.class))).thenReturn(0);
        Mockito.when(apiCommands.hello(any(HttpServletRequest.class))).thenThrow(
            new RuntimeException("HealthControllerTest.testReadiness"));
        builder = MockMvcRequestBuilders.get(url);
        response = mockmvc.perform(builder).andReturn().getResponse();
        Assert.assertEquals(500, response.getStatus());
        Assert.assertEquals("Naming is not in readiness", response.getContentAsString());
    }
}