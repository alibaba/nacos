package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the {@link ParamCheckerFilter}.
 * @author lynn.lqp
 * @date 2023/11/7
 */
public class ParamCheckerFilterTest {

    private ParamCheckerFilter filter;

    private ControllerMethodsCache methodsCache;

    private ServerParamCheckConfig serverParamCheckConfig;

    private HttpServletRequest request;

    private HttpServletResponse response;

    private FilterChain chain;

    @BeforeEach
    public void setUp() {
        filter = new ParamCheckerFilter(mock(ControllerMethodsCache.class));
        methodsCache = mock(ControllerMethodsCache.class);
        serverParamCheckConfig = mock(ServerParamCheckConfig.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    public void testDoFilterParamCheckDisabled() throws IOException, ServletException {
        when(serverParamCheckConfig.isParamCheckEnabled()).thenReturn(false);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    public void testDoFilterMethodNotFound() throws IOException, ServletException {
        when(methodsCache.getMethod(request)).thenReturn(null);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
