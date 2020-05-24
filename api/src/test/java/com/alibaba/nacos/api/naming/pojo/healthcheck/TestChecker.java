package com.alibaba.nacos.api.naming.pojo.healthcheck;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

public class TestChecker extends AbstractHealthChecker {

    @JsonTypeInfo(use = Id.NAME, property = "type")
    public static final String TYPE = "TEST";

    private String testValue;

    public String getTestValue() {
        return testValue;
    }

    public void setTestValue(String testValue) {
        this.testValue = testValue;
    }

    public TestChecker() {
        setType(TYPE);
    }

    @Override
    public AbstractHealthChecker clone() throws CloneNotSupportedException {
        return null;
    }
}
