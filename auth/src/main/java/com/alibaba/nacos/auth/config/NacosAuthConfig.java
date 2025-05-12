/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.config;

/**
 * Nacos Auth configurations.
 *
 * @author xiweng.yy
 */
public interface NacosAuthConfig {
    
    /**
     * Get auth scope like 'server', 'server admin', 'console'.
     *
     * @return auth scope
     */
    String getAuthScope();
    
    /**
     * Whether nacos server or console auth enabled.
     *
     * @return {@code true} means enabled, otherwise {@code false}
     */
    boolean isAuthEnabled();
    
    /**
     * Get current auth plugin type.
     *
     * @return auth plugin type.
     */
    String getNacosAuthSystemType();
    
    /**
     * Whether support server identity to identify request from other nacos servers.
     *
     * @return {@code true} means supported, otherwise {@code false}
     */
    boolean isSupportServerIdentity();
    
    /**
     * Get server identity key.
     *
     * @return server identity key If {@link #isSupportServerIdentity()} return {@code true}, otherwise empty string.
     */
    String getServerIdentityKey();
    
    /**
     * Get server identity value.
     *
     * @return server identity value If {@link #isSupportServerIdentity()} return {@code true}, otherwise empty string.
     */
    String getServerIdentityValue();
}
