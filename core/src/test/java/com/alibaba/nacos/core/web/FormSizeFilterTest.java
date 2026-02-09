/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link FormSizeFilter} unit test.
 *
 * @author Huang Xiao
 */
@ExtendWith(MockitoExtension.class)
class FormSizeFilterTest {

    private static final long MAX_FORM_SIZE = 1024;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private FormSizeFilter formSizeFilter;

    @BeforeEach
    void setUp() {
        formSizeFilter = new FormSizeFilter(MAX_FORM_SIZE);
    }

    @Test
    void testDoFilterWithNonFormContentType() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);

        formSizeFilter.doFilter(request, response, filterChain);

        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testDoFilterWithFormContentTypeUnderLimit() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(512);

        formSizeFilter.doFilter(request, response, filterChain);

        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testDoFilterWithFormContentTypeOverLimit() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(2048);

        formSizeFilter.doFilter(request, response, filterChain);

        verify(response).sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large");
    }

    @Test
    void testDoFilterWithExactLimitSize() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn((int) MAX_FORM_SIZE);

        formSizeFilter.doFilter(request, response, filterChain);

        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testDoFilterWithNegativeMaxFormSize() throws ServletException, IOException {
        FormSizeFilter unlimitedFilter = new FormSizeFilter(-1);
        Mockito.when(request.getContentType()).thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(Integer.MAX_VALUE);

        unlimitedFilter.doFilter(request, response, filterChain);

        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testDoFilterWithNullContentType() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn(null);

        formSizeFilter.doFilter(request, response, filterChain);

        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testDoFilterWithZeroMaxFormSize() throws ServletException, IOException {
        FormSizeFilter zeroSizeFilter = new FormSizeFilter(0);
        Mockito.when(request.getContentType()).thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(1);

        zeroSizeFilter.doFilter(request, response, filterChain);

        verify(response).sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large");
    }

    @Test
    void testDoFilterWithInvalidContentType() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn("invalid/content-type");

        formSizeFilter.doFilter(request, response, filterChain);

        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testFormSizeFilterStopsChainWhenSizeExceeded() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(2048);

        formSizeFilter.doFilter(request, response, filterChain);

        // 验证返回了错误
        verify(response).sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large");
        // 验证过滤器链被中断，后续过滤器不会执行
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testFormSizeFilterContinuesChainWhenSizeNormal() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(512);

        formSizeFilter.doFilter(request, response, filterChain);

        // 验证不返回错误
        verify(response, never()).sendError(anyInt(), anyString());
        // 验证过滤器链继续执行，后续过滤器（如 AuthFilter）会被调用
        verify(filterChain).doFilter(request, response);
    }
}
