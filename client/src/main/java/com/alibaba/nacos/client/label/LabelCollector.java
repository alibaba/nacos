package com.alibaba.nacos.client.label;

import java.util.Map;

/**
 * client labels collector.
 */
public interface LabelCollector {
    
    /**
     * get all labels.
     * @return labels set by developers.
     */
    Map<String, String> getLabels();
    
    /**
     * get label value by label name.
     *
     * @param labelName label name
     * @return label value
     */
    String getLabel(String labelName);
}
