package com.alibaba.nacos.auth;


public class AuthPlugin {
    
    /**
     * Plugin name
     */
    private String pluginName;
    
    /**
     * jar path
     */
    private String jarPath;
    
    /**
     * class name
     */
    private String className;
    
    
    public String getJarPath() {
        return jarPath;
    }
    
    public String getClassName() {
        return className;
    }
    
    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }
    
    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public String getPluginName() {
        return pluginName;
    }
    
}
