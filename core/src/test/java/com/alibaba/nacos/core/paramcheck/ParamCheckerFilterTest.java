package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.paramcheck.AbstractParamChecker;
import com.alibaba.nacos.common.paramcheck.ParamCheckResponse;
import com.alibaba.nacos.common.paramcheck.ParamCheckerManager;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.controller.CoreOpsController;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

@RunWith(SpringJUnit4ClassRunner.class)
public class ParamCheckerFilterTest {
    
    
    MockedStatic<ServerParamCheckConfig> serverParamCheckConfigMockedStatic;
    
    MockedStatic<ParamCheckerManager> paramCheckerManagerMockedStatic;
    
    MockedStatic<ExtractorManager> extractorManagerMockedStatic;
    
    @Mock
    ServerParamCheckConfig serverParamCheckConfig;
    
    @Mock
    ParamCheckerManager paramCheckerManager;
    
    @Before
    public void before() {
        paramCheckerManagerMockedStatic = Mockito.mockStatic(ParamCheckerManager.class);
        paramCheckerManagerMockedStatic.when(() -> ParamCheckerManager.getInstance()).thenReturn(paramCheckerManager);
        serverParamCheckConfigMockedStatic = Mockito.mockStatic(ServerParamCheckConfig.class);
        serverParamCheckConfigMockedStatic.when(() -> ServerParamCheckConfig.getInstance())
                .thenReturn(serverParamCheckConfig);
        extractorManagerMockedStatic = Mockito.mockStatic(ExtractorManager.class);
        
    }
    
    @After
    public void after() {
        paramCheckerManagerMockedStatic.close();
        serverParamCheckConfigMockedStatic.close();
        extractorManagerMockedStatic.close();
    }
    
    @Test
    public void testParamCheckNotEnabled() throws ServletException, IOException {
        ControllerMethodsCache methodsCache = Mockito.mock(ControllerMethodsCache.class);
        ParamCheckerFilter paramCheckerFilter = new ParamCheckerFilter(methodsCache);
        HttpServletRequest req = Mockito.mock(MockHttpServletRequest.class);
        HttpServletResponse resp = Mockito.mock(MockHttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(MockFilterChain.class);
        Mockito.when(serverParamCheckConfig.isParamCheckEnabled()).thenReturn(false);
        
        paramCheckerFilter.doFilter(req, resp, filterChain);
        
        Mockito.verify(filterChain, times(1)).doFilter(req, resp);
        Mockito.verify(methodsCache, times(0)).getMethod(any());
    }
    
    @Test
    public void testParamCheckNoExtractor() throws Exception {
        ControllerMethodsCache methodsCache = Mockito.mock(ControllerMethodsCache.class);
        ParamCheckerFilter paramCheckerFilter = new ParamCheckerFilter(methodsCache);
        HttpServletRequest req = Mockito.mock(MockHttpServletRequest.class);
        HttpServletResponse resp = Mockito.mock(MockHttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(MockFilterChain.class);
        Mockito.when(serverParamCheckConfig.isParamCheckEnabled()).thenReturn(true);
        Method noExtractorMethod = CoreOpsController.class.getDeclaredMethod("raftOps", Map.class);
        Mockito.when(methodsCache.getMethod(eq(req))).thenReturn(noExtractorMethod);
        //test
        paramCheckerFilter.doFilter(req, resp, filterChain);
        //verify
        Mockito.verify(filterChain, times(1)).doFilter(req, resp);
        Mockito.verify(paramCheckerManager, times(0)).getParamChecker(any());
    }
    
    @Test
    public void testParamCheckNoMethod() throws Exception {
        ControllerMethodsCache methodsCache = Mockito.mock(ControllerMethodsCache.class);
        ParamCheckerFilter paramCheckerFilter = new ParamCheckerFilter(methodsCache);
        HttpServletRequest req = Mockito.mock(MockHttpServletRequest.class);
        HttpServletResponse resp = Mockito.mock(MockHttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(MockFilterChain.class);
        Mockito.when(serverParamCheckConfig.isParamCheckEnabled()).thenReturn(true);
        Mockito.when(methodsCache.getMethod(eq(req))).thenReturn(null);
        paramCheckerFilter.doFilter(req, resp, filterChain);
        Mockito.verify(filterChain, times(1)).doFilter(req, resp);
        Mockito.verify(paramCheckerManager, times(0)).getParamChecker(any());
        
    }
    
    @Test
    public void testParamCheckPassed() throws Exception {
        ControllerMethodsCache methodsCache = Mockito.mock(ControllerMethodsCache.class);
        ParamCheckerFilter paramCheckerFilter = new ParamCheckerFilter(methodsCache);
        HttpServletRequest req = Mockito.mock(MockHttpServletRequest.class);
        HttpServletResponse resp = Mockito.mock(MockHttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(MockFilterChain.class);
        Mockito.when(serverParamCheckConfig.isParamCheckEnabled()).thenReturn(true);
        Method withExtractorMethod = TestExtractorController.class.getDeclaredMethod("testExtractor", String.class);
        Mockito.when(methodsCache.getMethod(eq(req))).thenReturn(withExtractorMethod);
        Mockito.when(paramCheckerManager.getParamChecker(any())).thenReturn(new TestPassedParamChecker());
        extractorManagerMockedStatic.when(() -> ExtractorManager.getHttpExtractor(any()))
                .thenReturn(new TestExtractor());
        paramCheckerFilter.doFilter(req, resp, filterChain);
        
        Mockito.verify(filterChain, times(1)).doFilter(req, resp);
        Mockito.verify(resp, times(0)).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        
    }
    
    @Test
    public void testParamCheckFailed() throws Exception {
        
        HttpServletRequest req = Mockito.mock(MockHttpServletRequest.class);
        HttpServletResponse resp = Mockito.mock(MockHttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(MockFilterChain.class);
        Mockito.when(serverParamCheckConfig.isParamCheckEnabled()).thenReturn(true);
        Method withExtractorMethod = TestExtractorController.class.getDeclaredMethod("testExtractor", String.class);
        Mockito.when(paramCheckerManager.getParamChecker(any())).thenReturn(new TestFailedParamChecker());
        extractorManagerMockedStatic.when(() -> ExtractorManager.getHttpExtractor(any()))
                .thenReturn(new TestExtractor());
        
        ControllerMethodsCache methodsCache = Mockito.mock(ControllerMethodsCache.class);
        Mockito.when(methodsCache.getMethod(eq(req))).thenReturn(withExtractorMethod);
        
        ServletOutputStream servletOutputStream = Mockito.mock(ServletOutputStream.class);
        Mockito.when(resp.getOutputStream()).thenReturn(servletOutputStream);
        ParamCheckerFilter paramCheckerFilter = new ParamCheckerFilter(methodsCache);
        paramCheckerFilter.doFilter(req, resp, filterChain);
        
        Mockito.verify(filterChain, times(0)).doFilter(req, resp);
        Mockito.verify(resp, times(1)).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        
    }
    
    @Test
    public void testParamCheckFailedeButResponseError() throws Exception {
        
        FilterChain filterChain = Mockito.mock(MockFilterChain.class);
        Mockito.when(serverParamCheckConfig.isParamCheckEnabled()).thenReturn(true);
        Method withExtractorMethod = TestExtractorController.class.getDeclaredMethod("testExtractor", String.class);
        Mockito.when(paramCheckerManager.getParamChecker(any())).thenReturn(new TestFailedParamChecker());
        extractorManagerMockedStatic.when(() -> ExtractorManager.getHttpExtractor(any()))
                .thenReturn(new TestExtractor());
        
        ControllerMethodsCache methodsCache = Mockito.mock(ControllerMethodsCache.class);
        HttpServletRequest req = Mockito.mock(MockHttpServletRequest.class);
        Mockito.when(methodsCache.getMethod(eq(req))).thenReturn(withExtractorMethod);
        ServletOutputStream servletOutputStream = Mockito.mock(ServletOutputStream.class);
        HttpServletResponse resp = Mockito.mock(MockHttpServletResponse.class);
        Mockito.when(resp.getOutputStream()).thenReturn(servletOutputStream);
        doThrow(new IOException("test fail")).when(servletOutputStream).println(any());
        ParamCheckerFilter paramCheckerFilter = new ParamCheckerFilter(methodsCache);
        paramCheckerFilter.doFilter(req, resp, filterChain);
        Mockito.verify(filterChain, times(0)).doFilter(req, resp);
        Mockito.verify(resp, times(1)).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        
    }
    
    @Test
    public void testParamCheckException() throws Exception {
        
        HttpServletRequest req = Mockito.mock(MockHttpServletRequest.class);
        HttpServletResponse resp = Mockito.mock(MockHttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(MockFilterChain.class);
        Mockito.when(serverParamCheckConfig.isParamCheckEnabled()).thenReturn(true);
        Method withExtractorMethod = TestExtractorController.class.getDeclaredMethod("testExtractor", String.class);
        Mockito.when(paramCheckerManager.getParamChecker(any())).thenReturn(new TestFailedParamChecker());
        extractorManagerMockedStatic.when(() -> ExtractorManager.getHttpExtractor(any()))
                .thenReturn(new TestNacosExceptionExtractor());
        
        ControllerMethodsCache methodsCache = Mockito.mock(ControllerMethodsCache.class);
        Mockito.when(methodsCache.getMethod(eq(req))).thenReturn(withExtractorMethod);
        ParamCheckerFilter paramCheckerFilter = new ParamCheckerFilter(methodsCache);
        try {
            paramCheckerFilter.doFilter(req, resp, filterChain);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof NacosRuntimeException);
        }
        
        Mockito.verify(filterChain, times(0)).doFilter(req, resp);
        Mockito.verify(resp, times(0)).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        
    }
    
    
    @ExtractorManager.Extractor(httpExtractor = TestExtractor.class)
    class TestExtractorController {
        
        public void testExtractor(String param) {
        
        }
    }
    
    class TestExtractor extends AbstractHttpParamExtractor {
        
        @Override
        public List<ParamInfo> extractParam(HttpServletRequest request) throws NacosException {
            return null;
        }
    }
    
    class TestNacosExceptionExtractor extends AbstractHttpParamExtractor {
        
        @Override
        public List<ParamInfo> extractParam(HttpServletRequest request) throws NacosException {
            throw new NacosException();
        }
    }
    
    
    class TestPassedParamChecker extends AbstractParamChecker {
        
        @Override
        public String getCheckerType() {
            return null;
        }
        
        @Override
        public ParamCheckResponse checkParamInfoList(List<ParamInfo> paramInfos) {
            ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
            
        }
        
        @Override
        public void initParamCheckRule() {
        
        }
    }
    
    class TestFailedParamChecker extends AbstractParamChecker {
        
        @Override
        public String getCheckerType() {
            return null;
        }
        
        @Override
        public ParamCheckResponse checkParamInfoList(List<ParamInfo> paramInfos) {
            ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage("test failed");
            return paramCheckResponse;
            
        }
        
        @Override
        public void initParamCheckRule() {
        
        }
    }
}
