package com.alibaba.nacos.common.lifecycle;

/**
 * Define serveral lifecycle state.
 *
 * @author zongtanghu
 */
public enum LifeCycleState {

    STOPPED("STOPPED"),
    STARTING("STARTING"),
    STARTED("STARTED"),
    STOPPING("STOPPING"),
    FAILED("FAILED");

    private String name;

    LifeCycleState(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "LifeCycleState{" +
            "name='" + name + '\'' +
            '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
