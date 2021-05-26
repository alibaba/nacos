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

package com.alibaba.nacos.console.security.nacos;

import com.alibaba.nacos.auth.common.AuthConfigs;
import com.alibaba.nacos.auth.common.AuthSystemTypes;
import com.alibaba.nacos.console.filter.JwtAuthenticationTokenFilter;
import com.alibaba.nacos.console.security.nacos.users.NacosUserDetailsServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsUtils;

/**
 * Spring security config.
 *
 * @author Nacos
 */
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class NacosAuthConfig extends WebSecurityConfigurerAdapter {
    
    public static final String AUTHORIZATION_HEADER = "Authorization";
    
    public static final String SECURITY_IGNORE_URLS_SPILT_CHAR = ",";
    
    public static final String LOGIN_ENTRY_POINT = "/v1/auth/login";
    
    public static final String TOKEN_BASED_AUTH_ENTRY_POINT = "/v1/auth/**";
    
    public static final String TOKEN_PREFIX = "Bearer ";
    
    public static final String CONSOLE_RESOURCE_NAME_PREFIX = "console/";

    public static final String UPDATE_PASSWORD_ENTRY_POINT = CONSOLE_RESOURCE_NAME_PREFIX + "user/password";
    
    private static final String DEFAULT_ALL_PATH_PATTERN = "/**";
    
    private static final String PROPERTY_IGNORE_URLS = "nacos.security.ignore.urls";

    @Autowired
    private Environment env;
    
    @Autowired
    private JwtTokenManager tokenProvider;
    
    @Autowired
    private AuthConfigs authConfigs;
    
    @Autowired
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Autowired
    private LdapAuthenticationProvider ldapAuthenticationProvider;
    
    @Bean(name = BeanIds.AUTHENTICATION_MANAGER)
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
    
    @Override
    public void configure(WebSecurity web) {
        
        String ignoreUrls = null;
        if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            ignoreUrls = DEFAULT_ALL_PATH_PATTERN;
        } else if (AuthSystemTypes.LDAP.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            ignoreUrls = DEFAULT_ALL_PATH_PATTERN;
        }
        if (StringUtils.isBlank(authConfigs.getNacosAuthSystemType())) {
            ignoreUrls = env.getProperty(PROPERTY_IGNORE_URLS, DEFAULT_ALL_PATH_PATTERN);
        }
        if (StringUtils.isNotBlank(ignoreUrls)) {
            for (String each : ignoreUrls.trim().split(SECURITY_IGNORE_URLS_SPILT_CHAR)) {
                web.ignoring().antMatchers(each.trim());
            }
        }
    }
    
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
        } else if (AuthSystemTypes.LDAP.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            auth.authenticationProvider(ldapAuthenticationProvider);
        }
    }
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        
        if (StringUtils.isBlank(authConfigs.getNacosAuthSystemType())) {
            http.csrf().disable().cors()// We don't need CSRF for JWT based authentication
                    .and().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and().authorizeRequests().requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                    .antMatchers(LOGIN_ENTRY_POINT).permitAll()
                    .and().authorizeRequests().antMatchers(TOKEN_BASED_AUTH_ENTRY_POINT).authenticated()
                    .and().exceptionHandling().authenticationEntryPoint(new JwtAuthenticationEntryPoint());
            // disable cache
            http.headers().cacheControl();
            
            http.addFilterBefore(new JwtAuthenticationTokenFilter(tokenProvider),
                    UsernamePasswordAuthenticationFilter.class);
        }
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
}
