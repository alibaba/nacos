/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * A filter used to limit the size of form data; see: [#14423](https://github.com/alibaba/nacos/issues/14423)
 *
 * @author Huang Xiao
 * @version 1.0.0
 */
public class FormSizeFilter implements Filter {
    
    private final long maxFormSize;
    
    public FormSizeFilter(long maxFormSize) {
        this.maxFormSize = maxFormSize;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        if (exceededFormSize(req)) {
            HttpServletResponse resp = (HttpServletResponse) response;
            resp.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large");
            return;
        }
        chain.doFilter(request, response);
    }
    
    /**
     * Check the size of form parameters.
     *
     * @param request HttpServletRequest
     */
    private boolean exceededFormSize(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null
            || !MediaType.APPLICATION_FORM_URLENCODED.equals(MediaType.valueOf(contentType))) {
            return false;
        }
        int contentLength = request.getContentLength();
        return (maxFormSize >= 0) && (contentLength > maxFormSize);
    }
}
