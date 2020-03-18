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

import com.alibaba.nacos.console.security.nacos.JwtTokenManager;
import com.alibaba.nacos.console.shell.NacosShellHandler;
import com.alibaba.nacos.core.auth.AccessException;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author <a href="mailto:liaochunyhm@live.com">liaochuntao</a>
 */
@Configuration
@EnableWebSocket
public class NacosShellConfig implements WebSocketConfigurer {

	@Autowired
	private JwtTokenManager tokenManager;

	@Override
	public void registerWebSocketHandlers(
			WebSocketHandlerRegistry registry) {
		registry.addHandler(new NacosShellHandler(), "/nacos/shell")
				.setAllowedOrigins("*")
				.addInterceptors(new AuthWebSocketInterceptor());
	}

	class AuthWebSocketInterceptor implements HandshakeInterceptor {

		@Override
		public boolean beforeHandshake(ServerHttpRequest request,
				ServerHttpResponse response, WebSocketHandler handler,
				Map<String, Object> attribute) throws Exception {
			if (!(request instanceof ServletServerHttpRequest)) {
				return true;
			}
			HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
			String token = servletRequest.getParameter("token");
			try {
				tokenManager.validateToken(token);
			} catch (ExpiredJwtException e) {
				response.getBody().write("token expired!".getBytes(StandardCharsets.UTF_8));
				return false;
			} catch (Exception e) {
				response.getBody().write("token invalid!".getBytes(StandardCharsets.UTF_8));
				return false;
			}
			return true;
		}

		@Override
		public void afterHandshake(ServerHttpRequest request,
				ServerHttpResponse response, WebSocketHandler handler,
				Exception e) {

		}
	}
}
