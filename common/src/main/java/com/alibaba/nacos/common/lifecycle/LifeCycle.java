package com.alibaba.nacos.common.lifecycle;

/**
 *
 * The lifecycle interface for generic service. Classes are need to implement
 * this interface have a defined life cycle defined by the methods of this interface.
 *
 * @author zongtanghu
 */
public interface LifeCycle {

    /**
     * Starts the service.
     *
     * @throws Exception If the service fails to start.
     */
    public void start() throws Exception;

    /**
     * Stops the service.
     *
     * @throws Exception If the service fails to stop.
     */
    public void stop() throws Exception;

    /**
     * This method will return the service whether is running or not.
     *
     * @return true if the component is starting or has been started.
     */
    public boolean isRunning();

    /**
     * This method will return the service whether is started or not.
     *
     * @return true if the service has been started.
     *
     */
    public boolean isStarted();

    /**
     * This method will return the service whether is starting or not.
     *
     * @return true if the component is starting.
     *
     */
    public boolean isStarting();

    /**
     * This method will return the service whether is stopping or not.
     *
     * @return true if the service is stopping.
     */
    public boolean isStopping();

    /**
     * This method will return the service whether is stopped or not.
     *
     * @return true if the service has been stopped.
     */
    public boolean isStopped();

    /**
     * This method will return the service whether is failed or not.
     *
     * @return true if the service has failed to start or has failed to stop.
     */
    public boolean isFailed();
}
