/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.console.filter;

import com.alibaba.nacos.console.config.WebSecurityConfig;
import com.alibaba.nacos.console.utils.JWTTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * jwt auth token filter
 *
 * @author wfnuser
 */
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);

    private JWTTokenUtils tokenProvider;

    public JwtAuthenticationTokenFilter(JWTTokenUtils tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String jwt = resolveToken(request);

        if (!StringUtils.isEmpty(jwt.trim()) && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (this.tokenProvider.validateToken(jwt)) {
                //获取用户认证信息
                Authentication authentication = this.tokenProvider.getAuthentication(jwt);
                //将用户保存到SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        chain.doFilter(request, response);
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
