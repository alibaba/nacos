package com.alibaba.nacos.plugin.control.tps.mse.key;

public enum MatchType {
    
    NO_MATCH(false, false, "no ", "no match"),
    
    EXACT(true, false, "exact", "exact match"),
    
    PREFIX(true, true, "prefix", "prefix match"),
    
    POSTFIX(true, true, "postfix", "postfix match"),
    
    PRE_POSTFIX(true, true, "pre_postfix", "pre and post match"),
    
    ALL(true, true, "all match", "*,all match");
    
    private boolean match;
    
    private boolean fuzzy;
    
    private String type;
    
    private String desc;
    
    MatchType(boolean match, boolean fuzzy, String type, String desc) {
        this.match = match;
        this.fuzzy = fuzzy;
        this.type = type;
        this.desc = desc;
    }
    
    public boolean isMatch() {
        return match;
    }
    
    public boolean isFuzzy() {
        return fuzzy;
    }
    
    public String getType() {
        return type;
    }
    
    public String getDesc() {
        return desc;
    }
}
