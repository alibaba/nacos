package com.alibaba.nacos.auth;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;

/**
 * Load Plugins.
 *
 * @author Wuyfee
 */
public class AuthPluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthPluginManager.class);
    
    public AuthPluginManager() {
        initAuthService();
        initResourceProvider();
    }
    
    /**
     * init AuthService.
     */
    public void initAuthService() {
        
        Collection<AuthService> authServices = NacosServiceLoader.load(AuthService.class);
        
        Iterator<AuthService> authIterator = authServices.iterator();
        boolean pluginNotFound = true;
        if (authIterator.hasNext()) {
            pluginNotFound = false;
        }
        if (pluginNotFound) {
            LOGGER.error("[AuthPluginManager] Load AuthPlugin({}) fail.", AuthService.class);
        } else {
            for (AuthService authsercice : authServices) {
                LOGGER.info(String.valueOf("[AuthPluginManager] Load {}({}) successfully."), authsercice.getClass(),
                        AuthService.class);
            }
        }
    }
    
    /**
     * init ResourceProvider.
     */
    public void initResourceProvider() {
        
        Collection<ResourceProvider> resourceProviders = NacosServiceLoader.load(ResourceProvider.class);
        
        Iterator<ResourceProvider> resourceIterator = resourceProviders.iterator();
        boolean pluginNotFound = true;
        if (resourceIterator.hasNext()) {
            pluginNotFound = false;
        }
        if (pluginNotFound) {
            LOGGER.error("[AuthPluginManager] Load AuthPlugin({}) fail.", ResourceProvider.class);
        } else {
            for (ResourceProvider resourceProvider : resourceProviders) {
                LOGGER.info(String.valueOf("[AuthPluginManager] Load {}({}) successfully."), resourceProvider.getClass(),
                        ResourceProvider.class);
            }
        }
    }
}
