package com.alibaba.nacos.client.auth;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ClientAuthService classLoader.
 *
 * @author wuyfee
 */
public class ClientAuthPluginManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAuthPluginManager.class);
    
    private static final ClientAuthPluginManager INSTANCE = new ClientAuthPluginManager();
    
    /**
     * The relationship of context type and {@link ClientAuthService}.
     */
    private Map<String, ClientAuthService> clientAuthServiceHashMap = new HashMap<>();
    
    public ClientAuthPluginManager() {
        initClientAuthServices();
    }
    
    public static ClientAuthPluginManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * init ClientAuthService.
     */
    private void initClientAuthServices() {
        Collection<ClientAuthService> clientAuthServices = NacosServiceLoader.load(ClientAuthService.class);
        for (ClientAuthService clientAuthService : clientAuthServices) {
            if (StringUtils.isEmpty(clientAuthService.getClientAuthServiceName())) {
                LOGGER.warn(
                        "[ClientAuthPluginManager] Load ClientAuthService({}) ClientAuthServiceName(null/empty) fail. "
                                + "Please Add ClientAuthServiceName to resolve.", clientAuthService.getClass());
                continue;
            }
            clientAuthServiceHashMap.put(clientAuthService.getClientAuthServiceName(), clientAuthService);
            LOGGER.info("[ClientAuthPluginManager] Load ClientAuthService({}) ClientAuthServiceName({}) successfully.",
                    clientAuthService.getClass(), clientAuthService.getClientAuthServiceName());
        }
    }
    
    /**
     * get ClientAuthService instance which ClientAuthService.getType() is type.
     * @param clientAuthServiceName AuthServiceName, mark a AuthService instance.
     * @return AuthService instance.
     */
    public Optional<ClientAuthService> findAuthServiceSpiImpl(String clientAuthServiceName) {
        return Optional.ofNullable(clientAuthServiceHashMap.get(clientAuthServiceName));
    }
    
}
