package com.alibaba.nacos.console.security;


import com.alibaba.nacos.console.domain.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {

        // TODO: get user from database
        User user = new User();
        user.setPassword("123456");
        user.setUsername("nacos");
        return new CustomUserDetails(user);
    }
}
