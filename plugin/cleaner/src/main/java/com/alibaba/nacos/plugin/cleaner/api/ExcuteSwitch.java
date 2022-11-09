package com.alibaba.nacos.plugin.cleaner.api;

/**
 * excute switch.
 *
 * @author vivid
 */
public interface ExcuteSwitch {
    
    /**
     * can this server excute  clean task.
     *
     * @return true for can
     */
    boolean canExcute();
}
