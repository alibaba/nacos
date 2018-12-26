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
        user.setPassword("$2a$04$l55XHWJ80UfbNXHIhFiunuqG07N2fOSmxqQEgNqijTY9tI/P0rnM6");
        user.setUsername("nacos");
        return new CustomUserDetails(user);
    }
}
