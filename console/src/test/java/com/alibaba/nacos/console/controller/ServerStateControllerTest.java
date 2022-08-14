package com.alibaba.nacos.console.controller;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * ServerStateController unit test.
 * @ClassName: ServerStateControllerTest
 * @Author: ChenHao26
 * @Date: 2022/8/13 10:54
 * @Description: TODO
 */
@RunWith(MockitoJUnitRunner.class)
public class ServerStateControllerTest {
    
    @InjectMocks
    private ServerStateController serverStateController;
    
    private MockMvc mockmvc;
    
    private static final String CONSOLE_URL = "/v1/console/server/state";
    
    @Before
    public void setUp() {
        mockmvc = MockMvcBuilders.standaloneSetup(serverStateController).build();
    }
    
    @Test
    public void serverState() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(CONSOLE_URL);
        Assert.assertEquals(200, mockmvc.perform(builder).andReturn().getResponse().getStatus());
    }
}
