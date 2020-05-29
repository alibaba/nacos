package com.alibaba.nacos.common.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EventListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Basic implementation of the life cycle interface for services.
 *
 * @author zongtanghu
 */
public abstract class AbstractLifeCycle implements LifeCycle{

    private static final Logger LOG = LoggerFactory.getLogger(AbstractLifeCycle.class);

    private final Object lock = new Object();
    private volatile LifeCycleState state = LifeCycleState.STOPPED;
    private final List<EventListener>  eventListeners = new CopyOnWriteArrayList<>();

    /**
     * Method to override to start the lifecycle.
     *
     * @throws Exception If there was a problem starting. Will cause a transition to FAILED state
     */
    protected void doStart() throws Exception {
    }

    /**
     * Method to override to stop the lifecycle.
     *
     * @throws Exception If there was a problem stopping. Will cause a transition to FAILED state
     */
    protected void doStop() throws Exception {
    }

    @Override
    public void start() throws Exception {
        synchronized (this.lock)
        {
            try
            {
                switch (state) {
                    case STARTED:
                        return;
                    case STARTING:
                    case STOPPING:
                        throw new IllegalStateException(getState());
                    default:
                        try {
                            setStarting();
                            doStart();
                            setStarted();
                        } catch (Exception e) {
                            if (LOG.isDebugEnabled())
                                LOG.debug("Unable to stop", e);
                            setStopping();
                            doStop();
                            setStopped();
                        }
                }
            } catch (Throwable e) {
                setFailed(e);
                throw e;
            }
        }
    }

    @Override
    public void stop() throws Exception {
        synchronized (this.lock) {
            try
            {
                switch (this.state) {
                    case STOPPED:
                        return;
                    case STARTING:
                    case STOPPING:
                        throw new IllegalStateException(getState());
                    default:
                        setStopping();
                        doStop();
                        setStopped();
                }
            } catch (Throwable e) {
                setFailed(e);
                throw e;
            }
        }
    }

    @Override
    public boolean isRunning() {
        switch (this.state)
        {
            case STARTED:
            case STARTING:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean isStarted() {
        return this.state == LifeCycleState.STARTED;
    }

    @Override
    public boolean isStarting() {
        return this.state == LifeCycleState.STARTING;
    }

    @Override
    public boolean isStopping() {
        return this.state == LifeCycleState.STOPPING;
    }

    @Override
    public boolean isStopped() {
        return this.state == LifeCycleState.STOPPED;
    }

    @Override
    public boolean isFailed() {
        return this.state == LifeCycleState.FAILED;
    }

    @Override
    public boolean addLifeCycleListener(LifeCycleListener listener) {
        if (this.eventListeners.contains(listener)) {
            return false;
        }
        return this.eventListeners.add(listener);
    }

    @Override
    public boolean removeLifeCycleListener(LifeCycleListener listener) {
        return this.eventListeners.remove(listener);
    }

    /**
     * Get the service's current state and return.
     *
     * @return service's current state.
     */
    public String getState() {
        return this.state.toString();
    }

    /**
     * If the service's current status is STARTING state, it will set STOPPED state.
     * And the method will execute lifeCycleStopped event if it has already been registered before.
     *
     */
    private void setStarted() {
        if (this.state == LifeCycleState.STARTING) {
            this.state = LifeCycleState.STARTED;
            if (LOG.isDebugEnabled())
                LOG.debug("STARTED {}", this);

            for (EventListener listener : eventListeners)
                if (listener instanceof LifeCycleListener)
                    ((LifeCycleListener)listener).lifeCycleStarted(this);
        }
    }

    /**
     * The service which implement AbstractLifeCycle will set STARTING state.
     * And it will execute lifeCycleStarting event if it has already been registered before.
     *
     */
    private void setStarting() {
        if (LOG.isDebugEnabled())
            LOG.debug("STARTING {}", this);
        this.state = LifeCycleState.STARTING;

        for (EventListener listener : this.eventListeners) {
            if (listener instanceof LifeCycleListener)
                ((LifeCycleListener) listener).lifeCycleStarting(this);
        }
    }

    /**
     * The service which implement AbstractLifeCycle will set STOPPING state.
     * And it will execute lifeCycleStopping event if it has been registered before.
     *
     */
    private void setStopping() {
        if (LOG.isDebugEnabled())
            LOG.debug("STOPPING {}", this);
        this.state = LifeCycleState.STOPPING;
        for (EventListener listener : this.eventListeners) {
            if (listener instanceof LifeCycleListener)
                ((LifeCycleListener) listener).lifeCycleStopping(this);
        }
    }

    /**
     * If the service's current status is STARTING state, it will set STOPPED state.
     * And the method will execute lifeCycleStopped event if it has already been registered before.
     *
     */
    private void setStopped() {
        if (this.state == LifeCycleState.STOPPING) {
            this.state = LifeCycleState.STOPPED;
            if (LOG.isDebugEnabled())
                LOG.debug("STOPPED {}", this);
            for (EventListener listener : this.eventListeners) {
                if (listener instanceof LifeCycleListener)
                    ((LifeCycleListener) listener).lifeCycleStopped(this);
            }
        }
    }

    /**
     * If some exceptions happen, the service will set FAILED state. And it will
     * execute lifeCycleFailure event if it has already been registered before.
     *
     * @param tb Exception which happens.
     */
    private void setFailed(Throwable tb) {
        this.state = LifeCycleState.FAILED;
        if (LOG.isDebugEnabled())
            LOG.warn("FAILED " + this + ": " + tb, tb);
        for (EventListener listener : this.eventListeners) {
            if (listener instanceof LifeCycleListener)
                ((LifeCycleListener)listener).lifeCycleFailure(this, tb);
        }
    }
}
