package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class LocalDiskRuleActivator implements RuleActivator {
    
    static LocalDiskRuleActivator INSTANCE = new LocalDiskRuleActivator();
    
    private LocalDiskRuleActivator() {
    
    }
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDiskRuleActivator.class);
    
    
    private File checkTpsBaseDir() {
        File baseDir = new File(EnvUtil.getNacosHome(), "data" + File.separator + "tps" + File.separator);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return baseDir;
    }
    
    private File getConnectionRuleFile() {
        File baseDir = new File(EnvUtil.getNacosHome(), "data" + File.separator + "connection" + File.separator);
        if (!baseDir.exists()) {
            baseDir.mkdir();
        }
        return new File(baseDir, "limitRule");
    }
    
    @Override
    public void saveConnectionRule(String ruleContent) throws IOException {
        File pointFile = getConnectionRuleFile();
        if (!pointFile.exists()) {
            pointFile.createNewFile();
        }
        String content = JacksonUtils.toJson(ruleContent);
        DiskUtils.writeFile(pointFile, content.getBytes(Constants.ENCODE), false);
        LOGGER.info("Save connection rule to local,pointName={}, ruleContent ={} ", content);
    }
    
    @Override
    public String getConnectionRule() {
        File connectionRuleFile = getConnectionRuleFile();
        if (!connectionRuleFile.exists()) {
            return null;
        }
        return DiskUtils.readFile(connectionRuleFile);
    }
    
    @Override
    public void saveTpsRule(String pointName, String ruleContent) throws IOException {
        File file = checkTpsBaseDir();
        File tpsFile = new File(file, pointName);
        if (!tpsFile.exists()) {
            tpsFile.createNewFile();
        }
        DiskUtils.writeFile(tpsFile, ruleContent.getBytes(Constants.ENCODE), false);
        LOGGER.info("Save tps rule to local,pointName={}, ruleContent ={} ", pointName, ruleContent);
        
    }
    
    @Override
    public String getTpsRule(String pointName) {
        File file = checkTpsBaseDir();
        File tpsFile = new File(file, pointName);
        if (!tpsFile.exists()) {
            return null;
        }
        return DiskUtils.readFile(tpsFile);
    }
}
