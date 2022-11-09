package com.alibaba.nacos.plugin.control.tps.rule;

public enum RuleModel {
    
    FUZZY("FUZZY", "every single monitor key will be counted as one counter"),
    PROTO("PROTO", "every single monitor key will be counted as different counter");
    
    private String model;
    
    private String desc;
    
    RuleModel(String model, String desc) {
        this.model = model;
        this.desc = desc;
    }
}
