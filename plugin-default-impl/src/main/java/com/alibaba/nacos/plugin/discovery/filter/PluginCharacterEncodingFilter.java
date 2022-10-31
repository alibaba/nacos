/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.discovery.filter;

import com.alibaba.nacos.plugin.discovery.HttpPluginServiceManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.boot.web.servlet.filter.OrderedCharacterEncodingFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plugin CharacterEncodingFilter.
 *
 * @author karsonto
 */
public class PluginCharacterEncodingFilter extends OrderedCharacterEncodingFilter {
    
    private final Pattern[] patterns;
    
    public PluginCharacterEncodingFilter(HttpPluginServiceManager httpPluginServiceManager) {
        List<String> urlPatterns = httpPluginServiceManager.getUrlPatterns();
        int size = urlPatterns.size();
        patterns = new Pattern[size];
        for (int i = 0; i < size; i++) {
            patterns[i] = Pattern.compile(EnvUtil.getContextPath() + urlPatterns.get(i));
        }
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String encoding = this.getEncoding();
        if (encoding != null) {
            if (this.isForceRequestEncoding() || request.getCharacterEncoding() == null) {
                request.setCharacterEncoding(encoding);
            }
            
            if (this.isForceResponseEncoding() || matchUri(request.getRequestURI())) {
                response.setCharacterEncoding(encoding);
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean matchUri(String uri) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(uri);
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    }
    
}
