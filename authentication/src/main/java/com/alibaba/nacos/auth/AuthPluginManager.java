package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.exception.AuthPluginException;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthPluginManager {
    private Map<String, Class> classMap = new HashMap<>();
    
    public AuthPluginManager(List<AuthPlugin> authplugins) throws AuthPluginException{
        initAuthPlugins(authplugins);
    }
    
    public void initAuthPlugins(AuthPlugin authPlugin) throws AuthPluginException{
        try{
            URL url = new File(authPlugin.getJarPath()).toURI().toURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[]{url});
            Class authclass = classLoader.loadClass(authPlugin.getClassName());
            classMap.put(authPlugin.getClassName(),authclass);
        }catch (Exception e){
            throw new AuthPluginException("AuthPlugin"+authPlugin.getPluginName()+" init error,"+e.getMessage());
        }
    }
    
    public void initAuthPlugins(List<AuthPlugin> authplugins) throws AuthPluginException{
        for (AuthPlugin authPlugin: authplugins){
            initAuthPlugins(authPlugin);
        }
    }
    
    public AuthService getInstance(String className) throws AuthPluginException{
        Class authclass = classMap.get(className);
        Object instance = null;
        try{
            instance = authclass.newInstance();
        }catch(Exception e){
            throw new AuthPluginException("AuthPlugin"+className+" instantiate error,"+e.getMessage());
        }
        return (AuthService) instance;
    }
    
}
