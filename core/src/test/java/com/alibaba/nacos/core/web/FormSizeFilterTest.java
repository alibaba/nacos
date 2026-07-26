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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

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
    
    /**
     * 测试非表单内容类型的请求
     * 场景：Content-Type 为 application/json
     * 预期：过滤器不进行大小检查，不返回错误
     */
    @Test
    void testDoFilterWithNonFormContentType() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);
        
        formSizeFilter.doFilter(request, response, filterChain);
        
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    /**
     * 测试表单内容类型且大小在限制内的请求
     * 场景：Content-Type 为 application/x-www-form-urlencoded，Content-Length 小于限制
     * 预期：正常处理，不返回错误
     */
    @Test
    void testDoFilterWithFormContentTypeUnderLimit() throws ServletException, IOException {
        Mockito.when(request.getContentType())
            .thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(512);
        
        formSizeFilter.doFilter(request, response, filterChain);
        
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    /**
     * 测试表单内容类型且大小超过限制的请求
     * 场景：Content-Type 为 application/x-www-form-urlencoded，Content-Length 大于限制
     * 预期：返回 431 Payload Too Large 错误
     */
    @Test
    void testDoFilterWithFormContentTypeOverLimit() throws ServletException, IOException {
        Mockito.when(request.getContentType())
            .thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(2048);
        
        formSizeFilter.doFilter(request, response, filterChain);
        
        verify(response).sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large");
    }
    
    /**
     * 测试表单大小等于限制值的边界情况
     * 场景：Content-Type 为 application/x-www-form-urlencoded，Content-Length 等于限制
     * 预期：正常处理，不返回错误
     */
    @Test
    void testDoFilterWithExactLimitSize() throws ServletException, IOException {
        Mockito.when(request.getContentType())
            .thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn((int) MAX_FORM_SIZE);
        
        formSizeFilter.doFilter(request, response, filterChain);
        
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    /**
     * 测试负数 maxFormSize 表示不限制大小的情况
     * 场景：maxFormSize 为 -1，Content-Length 为最大整数值
     * 预期：正常处理，不返回错误
     */
    @Test
    void testDoFilterWithNegativeMaxFormSize() throws ServletException, IOException {
        FormSizeFilter unlimitedFilter = new FormSizeFilter(-1);
        Mockito.when(request.getContentType())
            .thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(Integer.MAX_VALUE);
        
        unlimitedFilter.doFilter(request, response, filterChain);
        
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    /**
     * 测试 Content-Type 为 null 的情况
     * 场景：Content-Type 为 null
     * 预期：不进行大小检查，不返回错误
     */
    @Test
    void testDoFilterWithNullContentType() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn(null);
        
        formSizeFilter.doFilter(request, response, filterChain);
        
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    /**
     * 测试 maxFormSize 为 0 的情况
     * 场景：maxFormSize 为 0，Content-Length 为 1
     * 预期：任何正大小的请求都会被拒绝，返回 431 Payload Too Large 错误
     */
    @Test
    void testDoFilterWithZeroMaxFormSize() throws ServletException, IOException {
        FormSizeFilter zeroSizeFilter = new FormSizeFilter(0);
        Mockito.when(request.getContentType())
            .thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(1);
        
        zeroSizeFilter.doFilter(request, response, filterChain);
        
        verify(response).sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large");
    }
    
    /**
     * 测试无效的 Content-Type 格式
     * 场景：Content-Type 为无法解析的格式
     * 预期：不进行大小检查，不返回错误
     */
    @Test
    void testDoFilterWithInvalidContentType() throws ServletException, IOException {
        Mockito.when(request.getContentType()).thenReturn("invalid/content-type");
        
        formSizeFilter.doFilter(request, response, filterChain);
        
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    /**
     * 测试 FormSizeFilter 在表单过大时阻止后续过滤器执行
     * 场景：表单大小超过限制，FormSizeFilter 应该返回错误并中断过滤器链
     * 预期：FormSizeFilter 返回 431 错误，filterChain.doFilter 不会被调用
     * 这确保了 FormSizeFilter 在 AuthFilter 之前执行，避免超大表单进入认证逻辑
     */
    @Test
    void testFormSizeFilterStopsChainWhenSizeExceeded() throws ServletException, IOException {
        Mockito.when(request.getContentType())
            .thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(2048);
        
        formSizeFilter.doFilter(request, response, filterChain);
        
        // 验证返回了错误
        verify(response).sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large");
        // 验证过滤器链被中断，后续过滤器不会执行
        verify(filterChain, never()).doFilter(request, response);
    }
    
    /**
     * 测试 FormSizeFilter 在表单大小正常时允许后续过滤器执行
     * 场景：表单大小在限制内，FormSizeFilter 应该放行请求
     * 预期：不返回错误，filterChain.doFilter 被调用以执行后续过滤器
     * 这确保了正常的表单请求可以继续通过认证过滤器
     */
    @Test
    void testFormSizeFilterContinuesChainWhenSizeNormal() throws ServletException, IOException {
        Mockito.when(request.getContentType())
            .thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        Mockito.when(request.getContentLength()).thenReturn(512);
        
        formSizeFilter.doFilter(request, response, filterChain);
        
        // 验证不返回错误
        verify(response, never()).sendError(anyInt(), anyString());
        // 验证过滤器链继续执行，后续过滤器（如 AuthFilter）会被调用
        verify(filterChain).doFilter(request, response);
    }
}
