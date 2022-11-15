package com.alibaba.nacos.plugin.control.connection.mse;

public class CpuTestUtils {
    
    static boolean cpuOverLoad;
    
    public boolean isCpuOverLoad() {
        return cpuOverLoad;
    }
    
    public void setCpuOverLoad(boolean cpuOverLoad) {
        this.cpuOverLoad = cpuOverLoad;
    }
}
