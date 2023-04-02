package com.alibaba.nacos.naming.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@EnableGlobalMethodSecurity(prePostEnabled = true)
@ConditionalOnMissingClass("com.alibaba.nacos.plugin.auth.impl.NacosAuthConfig")
public class NacosNamingAuthConfig extends WebSecurityConfigurerAdapter {
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/**");
    }
}
