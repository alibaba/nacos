package com.alibaba.nacos.plugin.config.model;

import com.alibaba.nacos.config.server.model.SameConfigPolicy;

import java.io.Serializable;

/**
 * ConfigChangeNotifyInfo.
 *
 * @author liyunfei
 */
public class ConfigChangeNotifyInfo implements Serializable {
    
    private static final Long serialVersionUID = 19202931239213L;
    
    /**
     * Value of {@link com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes}.
     */
    private String action;
    
    /**
     * The result of handle.
     */
    private Boolean rs;
    
    /**
     * Time of config change.
     */
    private String modifyTime;
    
    private String errorMsg;
    
    private String dataId;
    
    private String group;
    
    private String tenant;
    
    private String content;
    
    private String srcIp;
    
    private String srcUser;
    
    private String use;
    
    private String appName;
    
    private String effect;
    
    private String type;
    
    private String desc;
    
    private String tag;
    
    private String configTags;
    
    private String namespace;
    
    private SameConfigPolicy policy;
    
    public ConfigChangeNotifyInfo(String action, Boolean rs, String modifyTime) {
        this.action = action;
        this.rs = rs;
        this.modifyTime = modifyTime;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public Boolean getRs() {
        return rs;
    }
    
    public void setRs(Boolean rs) {
        this.rs = rs;
    }
    
    public String getModifyTime() {
        return modifyTime;
    }
    
    public void setModifyTime(String modifyTime) {
        this.modifyTime = modifyTime;
    }
    
    public String getErrorMsg() {
        return errorMsg;
    }
    
    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getSrcIp() {
        return srcIp;
    }
    
    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }
    
    public String getSrcUser() {
        return srcUser;
    }
    
    public void setSrcUser(String srcUser) {
        this.srcUser = srcUser;
    }
    
    public String getUse() {
        return use;
    }
    
    public void setUse(String use) {
        this.use = use;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getEffect() {
        return effect;
    }
    
    public void setEffect(String effect) {
        this.effect = effect;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getDesc() {
        return desc;
    }
    
    public void setDesc(String desc) {
        this.desc = desc;
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public String getConfigTags() {
        return configTags;
    }
    
    public void setConfigTags(String configTags) {
        this.configTags = configTags;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public SameConfigPolicy getPolicy() {
        return policy;
    }
    
    public void setPolicy(SameConfigPolicy policy) {
        this.policy = policy;
    }
    
    @Override
    public String toString() {
        return "ConfigChangeNotifyInfo{" + "action='" + action + '\'' + ", rs=" + rs + ", modifyTime='" + modifyTime
                + '\'' + ", errorMsg='" + errorMsg + '\'' + ", dataId='" + dataId + '\'' + ", group='" + group + '\''
                + ", tenant='" + tenant + '\'' + ", content='" + content + '\'' + ", srcIp='" + srcIp + '\''
                + ", srcUser='" + srcUser + '\'' + ", use='" + use + '\'' + ", appName='" + appName + '\''
                + ", effect='" + effect + '\'' + ", type='" + type + '\'' + ", desc='" + desc + '\'' + ", tag='" + tag
                + '\'' + ", configTags='" + configTags + '\'' + ", namespace='" + namespace + '\'' + ", policy="
                + policy + '}';
    }
}
