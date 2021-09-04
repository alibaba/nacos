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

package com.alibaba.nacos.client.config;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigRequest;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.alibaba.nacos.client.config.impl.LocalEncryptedDataKeyProcessor;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.config.utils.ContentUtils;
import com.alibaba.nacos.client.config.utils.ParamUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.ValidatorUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Properties;

/**
 * Config Impl.
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosConfigService implements ConfigService {
    
    private static final Logger LOGGER = LogUtils.logger(NacosConfigService.class);
    
    private static final String UP = "UP";
    
    private static final String DOWN = "DOWN";
    
    /**
     * will be deleted in 2.0 later versions
     */
    @Deprecated
    ServerHttpAgent agent = null;
    
    /**
     * long polling.
     */
    private final ClientWorker worker;
    
    private String namespace;
    
    private final ConfigFilterChainManager configFilterChainManager;
    
    public NacosConfigService(Properties properties) throws NacosException {
        ValidatorUtils.checkInitParam(properties);
        
        initNamespace(properties);
        this.configFilterChainManager = new ConfigFilterChainManager(properties);
        ServerListManager serverListManager = new ServerListManager(properties);
        serverListManager.start();
        
        this.worker = new ClientWorker(this.configFilterChainManager, serverListManager, properties);
        // will be deleted in 2.0 later versions
        agent = new ServerHttpAgent(serverListManager);
        
    }
    
    private void initNamespace(Properties properties) {
        namespace = ParamUtil.parseNamespace(properties);
        properties.put(PropertyKeyConst.NAMESPACE, namespace);
    }
    
    @Override
    public String getConfig(String dataId, String group, long timeoutMs) throws NacosException {
        return getConfigInner(namespace, dataId, group, timeoutMs);
    }
    
    @Override
    public String getConfigAndSignListener(String dataId, String group, long timeoutMs, Listener listener)
            throws NacosException {
        String content = getConfig(dataId, group, timeoutMs);
        worker.addTenantListenersWithContent(dataId, group, content, Arrays.asList(listener));
        return content;
    }
    
    @Override
    public void addListener(String dataId, String group, Listener listener) throws NacosException {
        worker.addTenantListeners(dataId, group, Arrays.asList(listener));
    }
    
    @Override
    public boolean publishConfig(String dataId, String group, String content) throws NacosException {
        return publishConfig(dataId, group, content, ConfigType.getDefaultType().getType());
    }
    
    @Override
    public boolean publishConfig(String dataId, String group, String content, String type) throws NacosException {
        return publishConfigInner(namespace, dataId, group, null, null, null, content, type, null);
    }
    
    @Override
    public boolean publishConfigCas(String dataId, String group, String content, String casMd5) throws NacosException {
        return publishConfigInner(namespace, dataId, group, null, null, null, content,
                ConfigType.getDefaultType().getType(), casMd5);
    }
    
    @Override
    public boolean publishConfigCas(String dataId, String group, String content, String casMd5, String type)
            throws NacosException {
        return publishConfigInner(namespace, dataId, group, null, null, null, content, type, casMd5);
    }
    
    @Override
    public boolean removeConfig(String dataId, String group) throws NacosException {
        return removeConfigInner(namespace, dataId, group, null);
    }
    
    @Override
    public void removeListener(String dataId, String group, Listener listener) {
        worker.removeTenantListener(dataId, group, listener);
    }
    
    private String getConfigInner(String tenant, String dataId, String group, long timeoutMs) throws NacosException {
        // 如果group是空字符串的话，就使用默认的"DEFAULT_GROUP"来填充
        group = blank2defaultGroup(group);
        // 如果dataId为空，或者包含特殊字符，就抛400异常，异常信息"invalid"
        ParamUtils.checkKeyParam(dataId, group);
        // 构造配置查询返回结果
        ConfigResponse cr = new ConfigResponse();
        
        cr.setDataId(dataId);
        cr.setTenant(tenant);
        cr.setGroup(group);
        
        // use local config first
        // 优先使用本地容灾备份的配置，该配置需要用户手动创建。
        // 这样就保证了服务端宕机的情况，用户仍然能够正常通过这个方法获取配置，而不是一调用这个方法就去服务端获取，然后返回空。
        String content = LocalConfigInfoProcessor.getFailover(worker.getAgentName(), dataId, group, tenant);
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get failover ok, dataId={}, group={}, tenant={}, config={}",
                    worker.getAgentName(), dataId, group, tenant, ContentUtils.truncateContent(content));
            cr.setContent(content);
            //
            String encryptedDataKey = LocalEncryptedDataKeyProcessor
                    .getEncryptDataKeyFailover(agent.getName(), dataId, group, tenant);
            cr.setEncryptedDataKey(encryptedDataKey);
            // 在每次获取配置返回Response之前，都要拦截一下Response，来进行相应检查。
            // 具体检查的内容参考{@ConfigFilterChainManager}的注释。
            configFilterChainManager.doFilter(null, cr);
            content = cr.getContent();
            return content;
        }
        
        try {
            // 如果没有本地容灾备份文件，就在这一步去远程读取配置。
            ConfigResponse response = worker.getServerConfig(dataId, group, tenant, timeoutMs, false);
            cr.setContent(response.getContent());
            cr.setEncryptedDataKey(response.getEncryptedDataKey());
            // 在每次获取配置返回Response之前，都要拦截一下Response，来进行相应检查。
            // 具体检查的内容参考{@ConfigFilterChainManager}的注释。
            configFilterChainManager.doFilter(null, cr);
            content = cr.getContent();
            
            return content;
        } catch (NacosException ioe) {
            // 如果鉴权失败，就直接抛给用户异常。
            if (NacosException.NO_RIGHT == ioe.getErrCode()) {
                throw ioe;
            }
            LOGGER.warn("[{}] [get-config] get from server error, dataId={}, group={}, tenant={}, msg={}",
                    worker.getAgentName(), dataId, group, tenant, ioe.toString());
        }
        // 如果去服务端获取不到该配置，就去本地读快照文件（快照文件和容灾备份的路径不一样，详见{@link LocalConfigInfoProcessor#getFailoverFile}和{@link LocalConfigInfoProcessor#getSnapshotFile}的注释
        // 快照文件和容灾备份的区别在于：
        // 1、创建方式不同
        // 快照文件是去远程拉取配置时，自动创建的--详情参考ClientWorker.queryConfig中的LocalConfigInfoProcessor.saveSnapshot(this.getName(), dataId, group, tenant, response.getContent())语句
        // 而容灾备份是用户手动创建的。
        // 2、作用不同
        // 快照文件是为了在客户端和服务端的通信出现短暂故障时，仍能够保证方法正常返回。
        // 而容灾备份则是为了保证服务端彻底宕机时，仍能够通过用户手动创建配置，正常返回。
        LOGGER.warn("[{}] [get-config] get snapshot ok, dataId={}, group={}, tenant={}, config={}",
                worker.getAgentName(), dataId, group, tenant, ContentUtils.truncateContent(content));
        // 如果使用的是gRPC，则读取的默认路径是：${user.home}/nacos/config/config_rpc_client_nacos/snapshot/group/dataId
        content = LocalConfigInfoProcessor.getSnapshot(worker.getAgentName(), dataId, group, tenant);
        cr.setContent(content);
        // 从本地读取相应配置的加密后的dataKey，默认路径：${user.home}/nacos/config/config_rpc_client_nacos/encrypted-data-key/failover/group/dataId
        // 存疑：为什么是通过getEncryptDataKeyFailover，而不是通过getEncryptDataKeySnapshot读取？
        String encryptedDataKey = LocalEncryptedDataKeyProcessor
                .getEncryptDataKeyFailover(agent.getName(), dataId, group, tenant);
        cr.setEncryptedDataKey(encryptedDataKey);
        configFilterChainManager.doFilter(null, cr);
        content = cr.getContent();
        return content;
    }
    
    private String blank2defaultGroup(String group) {
        return (StringUtils.isBlank(group)) ? Constants.DEFAULT_GROUP : group.trim();
    }
    
    private boolean removeConfigInner(String tenant, String dataId, String group, String tag) throws NacosException {
        group = blank2defaultGroup(group);
        ParamUtils.checkKeyParam(dataId, group);
        return worker.removeConfig(dataId, group, tenant, tag);
    }
    
    /**
     * "新增配置"功能总入口，其他"新增配置"的方法均依赖此方法。
     *
     * @return 新增配置是否成功。
     */
    private boolean publishConfigInner(String tenant, String dataId, String group, String tag, String appName,
            String betaIps, String content, String type, String casMd5) throws NacosException {
        group = blank2defaultGroup(group);
        ParamUtils.checkParam(dataId, group, content);
        
        ConfigRequest cr = new ConfigRequest();
        cr.setDataId(dataId);
        cr.setTenant(tenant);
        cr.setGroup(group);
        cr.setContent(content);
        cr.setType(type);
        
        configFilterChainManager.doFilter(cr, null);
        content = cr.getContent();
        String encryptedDataKey = (String) cr.getParameter("encryptedDataKey");
        
        return worker
                .publishConfig(dataId, group, tenant, appName, tag, betaIps, content, encryptedDataKey, casMd5, type);
    }
    
    @Override
    public String getServerStatus() {
        if (worker.isHealthServer()) {
            return UP;
        } else {
            return DOWN;
        }
    }
    
    @Override
    public void shutDown() throws NacosException {
        worker.shutdown();
    }
}
