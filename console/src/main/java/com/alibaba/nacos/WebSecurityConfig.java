package com.alibaba.nacos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring security config
 *
 * @author Nacos
 */
@Configuration
@EnableWebSecurity(debug = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        // TODO 做开关是否开启登录功能
        if (false) {
            http.authorizeRequests().antMatchers("/").permitAll();
        } else {
            http.authorizeRequests()
                .antMatchers("/login.html", "/login", "/v1/cs/**", "/v1/ns/**").permitAll()
                .anyRequest().authenticated()
                .and().formLogin().loginPage("/login.html").loginProcessingUrl("/login").permitAll()
                .and().logout().logoutUrl("/logout").permitAll()   // 指定退出地址
                .and().csrf().disable().httpBasic();
        }
    }

    @Autowired
    protected void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .withUser("user").password("password").roles("USER").and()
            .withUser("admin").password("password").roles("USER", "ADMIN");
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return (NoOpPasswordEncoder) NoOpPasswordEncoder.getInstance();
    }

}
