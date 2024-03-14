package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.common.http.HttpUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
/**
 * {@link ReuseUploadFileHttpServletRequest} unit tests.
 * @author lynn.lqp
 * @date 2023/12/28
 */
public class ReuseUploadFileHttpServletRequestTest {

    private ReuseUploadFileHttpServletRequest reuseUploadFileHttpServletRequest;

    private MockMultipartHttpServletRequest mockMultipartHttpServletRequest;

    @BeforeEach
    public void setUp() throws MultipartException {
        mockMultipartHttpServletRequest = Mockito.mock(MockMultipartHttpServletRequest.class);
        when(mockMultipartHttpServletRequest.getParameterMap()).thenReturn(new HashMap<>());
        reuseUploadFileHttpServletRequest = new ReuseUploadFileHttpServletRequest(mockMultipartHttpServletRequest);
    }

    @Test
    public void testGetParameterMapEmpty() {
        Map<String, String[]> parameterMap = reuseUploadFileHttpServletRequest.getParameterMap();
        assertEquals(0, parameterMap.size());
    }

    @Test
    public void testGetParameterEmpty() {
        assertNull(reuseUploadFileHttpServletRequest.getParameter("nonExistentParam"));
    }

    @Test
    public void testGetParameterValuesEmpty() {
        assertNull(reuseUploadFileHttpServletRequest.getParameterValues("nonExistentParam"));
    }

    @Test
    public void testGetBodyWithoutFile() throws Exception {
        Object body = reuseUploadFileHttpServletRequest.getBody();
        assertEquals(HttpUtils.encodingParams(HttpUtils.translateParameterMap(new HashMap<>()), StandardCharsets.UTF_8.name()), body);
    }

}
