package com.alibaba.nacos.console.filter;

import com.alibaba.nacos.WebSecurityConfig;
import com.alibaba.nacos.console.utils.JWTTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


public class JwtAuthenticationTokenFilter extends GenericFilterBean {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);

    @Autowired
    private JWTTokenUtils tokenProvider;


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        System.out.println("JwtAuthenticationTokenFilter");
        HttpServletRequest httpReq = (HttpServletRequest) servletRequest;
        HttpServletResponse httpRes = (HttpServletResponse) servletResponse;

        String jwt = resolveToken(httpReq);
        System.out.println(jwt);
        //验证JWT是否正确
        try {
            if (StringUtils.hasText(jwt) && this.tokenProvider.validateToken(jwt)) {
                //获取用户认证信息
                Authentication authentication = this.tokenProvider.getAuthentication(jwt);
                //将用户保存到SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            httpRes.reset();
            httpRes.setHeader("Content-Type", "application/json;charset=UTF-8");
            httpRes.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private String resolveToken(HttpServletRequest request) {
        //从HTTP头部获取TOKEN
        String bearerToken = request.getHeader(WebSecurityConfig.AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            //返回Token字符串，去除Bearer
            return bearerToken.substring(7, bearerToken.length());
        }
        //从请求参数中获取TOKEN
        String jwt = request.getParameter(WebSecurityConfig.AUTHORIZATION_TOKEN);
        if (StringUtils.hasText(jwt)) {
            return jwt;
        }
        return null;
    }
}
