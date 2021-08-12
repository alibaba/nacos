package com.alibaba.nacos.auth.common;

import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.auth.context.GrpcIdentityContextBuilder;
import com.alibaba.nacos.auth.context.HttpIdentityContextBuilder;
import com.alibaba.nacos.auth.context.IdentityContext;
import com.alibaba.nacos.auth.exception.ConfigException;
import net.bytebuddy.asm.Advice;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.alibaba.nacos.api.remote.request.Request;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.xml.bind.Element;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpIdentityContextBuilderTest {

    HttpIdentityContextBuilder httpIdentityContextBuilder;
    
    GrpcIdentityContextBuilder grpcIdentityContextBuilder;
    
    @Before
    public void setUp(){
        httpIdentityContextBuilder = new HttpIdentityContextBuilder();
        grpcIdentityContextBuilder = new GrpcIdentityContextBuilder();
    }
    
    @Test
    public void testBuilt() throws ConfigException {
        HttpServletRequest httpRequest = new MockHttpServletRequest();
        Request grpcRequest = new ConnectionSetupRequest();
        Assert.assertNotNull(httpIdentityContextBuilder.build(httpRequest));
        Assert.assertNotNull(grpcIdentityContextBuilder.build(grpcRequest));
        
    }

}
