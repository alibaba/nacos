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
     * @return true if the component is starting or has been started.
     */
    public boolean isRunning();

    /**
     * @return true if the service has been started.
     *
     */
    public boolean isStarted();

    /**
     * @return true if the component is starting.
     *
     */
    public boolean isStarting();

    /**
     * @return true if the service is stopping.
     *
     */
    public boolean isStopping();

    /**
     * @return true if the service has been stopped.
     *
     */
    public boolean isStopped();

    /**
     * @return true if the component has failed to start or has failed to stop.
     */
    public boolean isFailed();

    /**
     * add a LifeCycleListener.
     *
     * @param listener event listener
     * @return the result whether to add LifeCycleListener or not.
     */
    public boolean addLifeCycleListener(LifeCycleListener listener);

    /**
     * remove a LifeCycleListener.
     *
     * @param listener event listener
     * @return the result whether to remove LifeCycleListener or not.
     */
    public boolean removeLifeCycleListener(LifeCycleListener listener);
}
