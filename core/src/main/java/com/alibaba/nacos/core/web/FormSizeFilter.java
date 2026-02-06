package com.alibaba.nacos.core.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

///
/// 用于限制表单数据大小的过滤器，详见：[#14423](https://github.com/alibaba/nacos/issues/14423)
///
/// @author Huang Xiao
/// @version 1.0.0
///

public class FormSizeFilter implements Filter {

    private final long maxFormSize;

    public FormSizeFilter(long maxFormSize) {
        this.maxFormSize = maxFormSize;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
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
        if (contentType == null || !MediaType.APPLICATION_FORM_URLENCODED.equals(MediaType.valueOf(contentType))) {
            return false;
        }
        int contentLength = request.getContentLength();
        return (maxFormSize >= 0) && (contentLength > maxFormSize);
    }
}
