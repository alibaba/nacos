package com.alibaba.nacos.client.label;

import java.util.HashMap;
import java.util.Map;

public class DefaultLabelCollectorImpl implements LabelCollector {
    
    @Override
    public Map<String, String> getLabels() {
        Map<String, String> environmentMap = getEnvironments();
        
        Map<String, String> jvmOptsMap = getJvmOptions();
        
        // jvm variables set by developer have priority over environment variables set by ASI;
        for (Map.Entry<String, String> entry : jvmOptsMap.entrySet()) {
            environmentMap.put(entry.getKey(), entry.getValue());
        }
        
        return environmentMap;
    }
    
    @Override
    public String getLabel(String labelName) {
        Map<String, String> map = getLabels();
        return map.get(labelName);
    }
    
    /**
     * get environment variables.
     *
     * @return environment variable map
     */
    private Map<String, String> getEnvironments() {
        // environment variables set by ASI
        Map<String, String> map = new HashMap<String, String>();
        String labelSite = "SIGMA_APP_SITE";
        String site = System.getenv(labelSite);
        String labelUnit = "SIGMA_APP_UNIT";
        String unit = System.getenv(labelUnit);
        String labelApp = "SIGMA_APP_NAME";
        String app = System.getenv(labelApp);
        
        if (!isStringEmpty(site)) {
            map.put("site", site);
        }
        
        if (!isStringEmpty(unit)) {
            map.put("unit", unit);
        }
        
        if (!isStringEmpty(app)) {
            map.put("app", app);
        }
    
        String labelStage = "SIGMA_APP_STAGE";
        String stage = System.getenv(labelStage);
        if (!isStringEmpty(stage)) {
            map.put("stage", stage);
        }
        //NACOS_ENV_LABELS=app:diamond,unit:CENTER_UNIT.center,site:na61,stage:PUBLISH
        String labelsString = System.getenv("NACOS_ENV_LABELS");
        
        // environment variables set by developer
        Map<String, String> customedLabels = getMapByString(labelsString);
        
        // Environment variables set by developer have priority over those set by ASI;
        for (Map.Entry<String, String> entry : customedLabels.entrySet()) {
            map.put(entry.getKey(), entry.getValue());
        }
        
        return map;
    }
    
    private Map<String, String> getMapByString(String labelsString) {
        Map<String, String> map = new HashMap<String, String>();
        
        if (!isStringEmpty(labelsString)) {
            String[] labelArray = labelsString.split(",");
            
            for (String label : labelArray) {
                String k = label.split(":")[0];
                String v = label.split(":")[1];
                map.put(k, v);
            }
        }
        
        return map;
    }
    
    private Map<String, String> getJvmOptions() {
        //-Dnacos.env.labels=app:diamond,unit:CENTER_UNIT.center,site:na61,stage:PUBLISH
        return getMapByString(System.getProperty("nacos.env.labels", ""));
    }
    
    private boolean isStringEmpty(String s) {
        String empty = "";
        return s == null || s.equals(empty);
    }
}
