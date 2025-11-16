/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.mcpregistry.config;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.firewall.StrictHttpFirewall;

/**
 * In the MCP community API definition, the MCP server name is passed as a path parameter.
 * Because the server name can contain '/', we need to configure Tomcat and WebSecurity
 * to allow '/' in the path.
 *
 * @author xinluo
 */
@Configuration
public class HttpPathConfiguration {

    @Bean
    public TomcatConnectorCustomizer connectorCustomizer() {
        return (connector) -> connector.setEncodedSolidusHandling(EncodedSolidusHandling.PASS_THROUGH.getValue());
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowUrlEncodedPercent(true);
        return (web) -> web.httpFirewall(firewall);
    }
}
