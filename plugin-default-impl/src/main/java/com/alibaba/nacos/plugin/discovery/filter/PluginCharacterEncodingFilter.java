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
            
            if (this.isForceResponseEncoding()) {
                response.setCharacterEncoding(encoding);
            } else if (matchUri(request.getRequestURI())) {
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
