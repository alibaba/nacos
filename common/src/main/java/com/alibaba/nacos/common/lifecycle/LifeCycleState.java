package com.alibaba.nacos.common.lifecycle;

/**
 * Define serveral lifecycle state.
 *
 * @author zongtanghu
 */
public enum LifeCycleState {

    /**
     * The service's current state is STOPPED.
     */
    STOPPED("STOPPED"),

    /**
     * The service's current state is STARTING.
     */
    STARTING("STARTING"),

    /**
     * The service's current state is STARTED.
     */
    STARTED("STARTED"),

    /**
     * The service's current state is STOPPING.
     */
    STOPPING("STOPPING"),

    /**
     * The service's current state is FAILED.
     */
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
