package com.alibaba.nacos.common.lifecycle;


import java.util.EventListener;

/**
 * A listener for Lifecycle events.
 *
 * @author zongtanghu
 */
public interface LifeCycleListener extends EventListener {

    /**
     * Listening for sevice starting event.
     *
     * @param event
     */
    default void lifeCycleStarting(LifeCycle event) { }

    /**
     * Listening for sevice started event.
     *
     * @param event
     */
    default void lifeCycleStarted(LifeCycle event) { }

    /**
     * Listening for sevice failure event.
     *
     * @param event
     * @param cause
     */
    default void lifeCycleFailure(LifeCycle event, Throwable cause) { }

    /**
     * Listening for sevice stopping event.
     *
     * @param event
     */
    default void lifeCycleStopping(LifeCycle event) { }

    /**
     * Listening for sevice stopped event.
     *
     * @param event
     */
    default void lifeCycleStopped(LifeCycle event) { }
}
