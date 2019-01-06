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
package com.alibaba.nacos.console.utils;

import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.List;


/**
 * Jwt token tool
 *
 * @author wfnuser
 */
@Component
public class JWTTokenUtils {

    private final Logger log = LoggerFactory.getLogger(JWTTokenUtils.class);

    private static final String AUTHORITIES_KEY = "auth";

    // 签名密钥
    private String secretKey;

    // 失效日期
    private long tokenValidityInMilliseconds;

    @PostConstruct
    public void init() {
        this.secretKey = "SecretKey";
        this.tokenValidityInMilliseconds = 1000 * 60 * 30L;
    }

    // 创建Token
    public String createToken(Authentication authentication) {
        // 获取当前时间戳
        long now = (new Date()).getTime();
        // 存放过期时间
        Date validity;
        validity = new Date(now + this.tokenValidityInMilliseconds);

        // 创建Token令牌
        return Jwts.builder()
            .setSubject(authentication.getName())
            .claim(AUTHORITIES_KEY, "")
            .setExpiration(validity)
            .signWith(SignatureAlgorithm.HS512, secretKey)
            .compact();
    }

    // 获取用户权限
    public Authentication getAuthentication(String token) {
        // 解析Token的payload
        Claims claims = Jwts.parser()
            .setSigningKey(secretKey)
            .parseClaimsJws(token)
            .getBody();

        List<GrantedAuthority> authorities = AuthorityUtils.commaSeparatedStringToAuthorityList((String) claims.get(AUTHORITIES_KEY));


        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    //验证Token是否正确
    public boolean validateToken(String token) {
        try {
            //通过密钥验证Token
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            return true;
        } catch (SignatureException e) {
            //签名异常
            log.info("Invalid JWT signature.");
            log.trace("Invalid JWT signature trace: {}", e);
        } catch (MalformedJwtException e) {
            //JWT格式错误
            log.info("Invalid JWT token.");
            log.trace("Invalid JWT token trace: {}", e);
        } catch (ExpiredJwtException e) {
            //JWT过期
            log.info("Expired JWT token.");
            log.trace("Expired JWT token trace: {}", e);
        } catch (UnsupportedJwtException e) {
            //不支持该JWT
            log.info("Unsupported JWT token.");
            log.trace("Unsupported JWT token trace: {}", e);
        } catch (IllegalArgumentException e) {
            //参数错误异常
            log.info("JWT token compact of handler are invalid.");
            log.trace("JWT token compact of handler are invalid trace: {}", e);
        }
        return false;
    }
}
