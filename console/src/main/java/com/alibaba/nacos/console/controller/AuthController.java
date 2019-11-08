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
package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.config.server.model.RestResult;
import com.alibaba.nacos.console.config.WebSecurityConfig;
import com.alibaba.nacos.console.security.CustomUserDetailsServiceImpl;
import com.alibaba.nacos.console.utils.JwtTokenUtils;
import com.alibaba.nacos.console.utils.PasswordEncoderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

/**
 * auth
 *
 * @author wfnuser
 */
@RestController("auth")
@RequestMapping("/v1/auth")
public class AuthController {

    @Autowired
    private JwtTokenUtils jwtTokenUtils;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private CustomUserDetailsServiceImpl userDetailsService;

    /**
     * Whether the Nacos is in broken states or not, and cannot recover except by being restarted
     *
     * @return HTTP code equal to 200 indicates that Nacos is in right states. HTTP code equal to 500 indicates that
     * Nacos is in broken states.
     */

    @PostMapping("login")
    public RestResult<String> login(@RequestParam String username, @RequestParam String password, HttpServletResponse response) {

        // 通过用户名和密码创建一个 Authentication 认证对象，实现类为 UsernamePasswordAuthenticationToken
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
        RestResult<String> rr = new RestResult<String>();

        try {
            //通过 AuthenticationManager（默认实现为ProviderManager）的authenticate方法验证 Authentication 对象
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            //将 Authentication 绑定到 SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
            //生成Token
            String token = jwtTokenUtils.createToken(authentication);
            //将Token写入到Http头部
            response.addHeader(WebSecurityConfig.AUTHORIZATION_HEADER, "Bearer " + token);
            rr.setCode(200);
            rr.setData("Bearer " + token);
            return rr;
        } catch (BadCredentialsException authentication) {
            rr.setCode(401);
            rr.setMessage("Login failed");
            return rr;
        }
    }

    @PutMapping("password")
    public RestResult<String> updatePassword(@RequestParam(value = "oldPassword") String oldPassword,
                                             @RequestParam(value = "newPassword") String newPassword) {

        RestResult<String> rr = new RestResult<String>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = ((UserDetails) principal).getUsername();
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String password = userDetails.getPassword();

        // TODO: throw out more fine grained exceptions
        try {
            if (PasswordEncoderUtil.matches(oldPassword, password)) {
                userDetailsService.updateUserPassword(username, PasswordEncoderUtil.encode(newPassword));
                rr.setCode(200);
                rr.setMessage("Update password success");
            } else {
                rr.setCode(401);
                rr.setMessage("Old password is invalid");
            }
        } catch (Exception e) {
            rr.setCode(500);
            rr.setMessage("Update userpassword failed");
        }
        return rr;
    }
}
