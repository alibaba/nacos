package com.alibaba.nacos.config.server.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * ConfigHistoryInfo including updated info.
 */
public class ConfigHistoryInfoPair extends ConfigHistoryInfo implements Serializable {

    private String updatedMd5;

    private String updatedContent;

    public String getUpdatedMd5() {
        return updatedMd5;
    }

    public void setUpdatedMd5(String updatedMd5) {
        this.updatedMd5 = updatedMd5;
    }

    public String getUpdatedContent() {
        return updatedContent;
    }

    public void setUpdatedContent(String updatedContent) {
        this.updatedContent = updatedContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigHistoryInfoPair that = (ConfigHistoryInfoPair) o;
        return super.equals(o) && Objects.equals(updatedMd5, that.updatedMd5)
                && Objects.equals(updatedContent, that.updatedContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), updatedMd5, updatedContent);
    }

}