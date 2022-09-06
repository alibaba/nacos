package com.alibaba.nacos.plugin.auth.impl.control;

import com.alibaba.nacos.plugin.control.TpsControlManager;

public class NacosTpsControlManager extends TpsControlManager {
    
    @Override
    public String getName() {
        return "nacos";
    }
}
