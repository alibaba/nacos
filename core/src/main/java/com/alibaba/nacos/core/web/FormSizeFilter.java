package com.alibaba.nacos.core.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

///
/// A filter used to limit the size of form data; see: [#14423](https://github.com/alibaba/nacos/issues/14423)
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
