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
package com.alibaba.nacos.console.config;

import com.alibaba.nacos.console.filter.JwtAuthenticationTokenFilter;
import com.alibaba.nacos.console.security.CustomUserDetailsService;
import com.alibaba.nacos.console.security.JwtAuthenticationEntryPoint;
import com.alibaba.nacos.console.utils.JWTTokenUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

/**
 * Spring security config
 *
 * @author Nacos
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String AUTHORIZATION_TOKEN = "access_token";

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @Autowired
    private JWTTokenUtils tokenProvider;

    @Bean(name = BeanIds.AUTHENTICATION_MANAGER)
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
    }

    @Override
    public void configure(WebSecurity web) {
        // TODO: we should use a better way to match the resources
        // requests for resource and auth api are always allowed
        web.ignoring()
            .antMatchers("/", "/*.html", "/**/*.js", "/**/*.css", "/favicon.ico", "/**/*.html", "/**/*.map", "/**/*.svg", "/console-fe/public/*", "/**/*.png", "/*.png")
            .antMatchers("/v1/auth/login")
            .antMatchers("/v1/cs/health");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        // TODO 做开关是否开启登录功能
        if (false) {
            http.authorizeRequests().antMatchers("/").permitAll();
        } else {
            http
                .authorizeRequests()
                .anyRequest().authenticated().and()
                // custom token authorize exception handler
                .exceptionHandling()
                .authenticationEntryPoint(unauthorizedHandler).and()
                // since we use jwt, session is not necessary
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                // since we use jwt, csrf is not necessary
                .csrf().disable();
            http.addFilterBefore(new JwtAuthenticationTokenFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

            // disable cache
            http.headers().cacheControl();
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
