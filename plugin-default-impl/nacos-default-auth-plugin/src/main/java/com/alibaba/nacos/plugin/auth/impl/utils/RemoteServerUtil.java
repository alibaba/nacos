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

package com.alibaba.nacos.plugin.auth.impl.utils;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.file.FileChangeEvent;
import com.alibaba.nacos.sys.file.FileWatcher;
import com.alibaba.nacos.sys.file.WatchFileCenter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nacos auth plugin remote nacos server util.
 *
 * @author xiweng.yy
 */
public class RemoteServerUtil {
    
    private static List<String> serverAddresses = new LinkedList<>();
    
    private static AtomicInteger index = new AtomicInteger();
    
    private static String remoteServerContextPath = "/nacos";
    
    static {
        readRemoteServerAddress();
        registerWatcher();
        initRemoteServerContextPath();
    }
    
    private static void initRemoteServerContextPath() {
        remoteServerContextPath = EnvUtil.getProperty("nacos.console.remote.server.context-path", "/nacos");
    }
    
    private static void registerWatcher() {
        try {
            WatchFileCenter.registerWatcher(EnvUtil.getClusterConfFilePath(), new FileWatcher() {
                
                @Override
                public void onChange(FileChangeEvent event) {
                    readRemoteServerAddress();
                }
                
                @Override
                public boolean interest(String context) {
                    return true;
                }
            });
        } catch (Exception ignored) {
        }
    }
    
    /**
     * Read nacos server address from cluster.conf.
     */
    public static void readRemoteServerAddress() {
        try {
            serverAddresses = EnvUtil.readClusterConf();
        } catch (IOException ignored) {
        }
    }
    
    public static List<String> getServerAddresses() {
        return new LinkedList<>(serverAddresses);
    }
    
    public static String getOneNacosServerAddress() {
        int actual = index.getAndUpdate(operand -> (operand + 1) % serverAddresses.size());
        return serverAddresses.get(actual);
    }
    
    public static String getRemoteServerContextPath() {
        return remoteServerContextPath;
    }
    
    /**
     * Single check http result, if not success, wrapper result as Nacos exception.
     *
     * @param result http execute result
     * @throws NacosException wrapper result as NacosException
     */
    public static void singleCheckResult(HttpRestResult<String> result) throws NacosException {
        if (result.ok()) {
            return;
        }
        throw new NacosException(result.getCode(), result.getMessage());
    }
    
    /**
     * According input {@link AuthConfigs} to build remote server identity header.
     *
     * @param authConfigs authConfigs
     * @return remote server identity header
     */
    public static Header buildServerRemoteHeader(AuthConfigs authConfigs) {
        Header header = Header.newInstance();
        if (StringUtils.isNotBlank(authConfigs.getServerIdentityKey())) {
            header.addParam(authConfigs.getServerIdentityKey(), authConfigs.getServerIdentityValue());
        }
        return header;
    }
}
