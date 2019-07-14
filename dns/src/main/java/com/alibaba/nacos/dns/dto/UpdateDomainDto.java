package com.alibaba.nacos.dns.dto;

import java.io.Serializable;

public class UpdateDomainDto implements Serializable {

    private Integer cacheTime;
    private String cName;

    public Integer getCacheTime() {
        return cacheTime;
    }

    public void setCacheTime(Integer cacheTime) {
        this.cacheTime = cacheTime;
    }

    public String getcName() {
        return cName;
    }

    public void setcName(String cName) {
        this.cName = cName;
    }
}
