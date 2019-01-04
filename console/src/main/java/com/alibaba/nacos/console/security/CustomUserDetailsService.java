package com.alibaba.nacos.console.security;


import com.alibaba.nacos.config.server.model.User;
import com.alibaba.nacos.config.server.service.PersistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private transient PersistService persistService;

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
    // 持久层写一个获取用户信息的sql
        User user = persistService.findUserByUsername(userName);
        if (user == null) {
            throw new UsernameNotFoundException(userName);
        }
        return new CustomUserDetails(user);
    }
}
