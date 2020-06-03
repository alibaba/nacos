/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.common.utils;

import java.util.Set;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Observable {

	private transient boolean changed = false;
	private transient Set<Observer> obs = new ConcurrentHashSet<Observer>();
	private volatile int observerCnt = 0;
	private boolean alreadyAddObserver = false;

	/**
	 * Adds an observer to the set of observers for this object, provided
	 * that it is not the same as some observer already in the set.
	 * The order in which notifications will be delivered to multiple
	 * observers is not specified. See the class comment.
	 *
	 * @param   o   an observer to be added.
	 * @throws NullPointerException   if the parameter o is null.
	 */
	public synchronized void addObserver(Observer o) {
		Objects.requireNonNull(o, "Observer");
		obs.add(o);
		observerCnt ++;
		if (!alreadyAddObserver) {
			notifyAll();
		}
		alreadyAddObserver = true;
	}

	/**
	 * Deletes an observer from the set of observers of this object.
	 * Passing {@code null} to this method will have no effect.
	 * @param   o   the observer to be deleted.
	 */
	public synchronized void deleteObserver(Observer o) {
		obs.remove(o);
		observerCnt --;
	}

	/**
	 * If this object has changed, as indicated by the
	 * {@code hasChanged} method, then notify all of its observers
	 * and then call the {@code clearChanged} method to
	 * indicate that this object has no longer changed.
	 * <p>
	 * Each observer has its {@code update} method called with two
	 * arguments: this observable object and {@code null}. In other
	 * words, this method is equivalent to:
	 * <blockquote>{@code
	 * notifyObservers(null)}</blockquote>
	 */
	public void notifyObservers() {
		notifyObservers(null);
	}

	/**
	 * If this object has changed, as indicated by the
	 * {@code hasChanged} method, then notify all of its observers
	 * and then call the {@code clearChanged} method to indicate
	 * that this object has no longer changed.
	 * <p>
	 * Each observer has its {@code update} method called with two
	 * arguments: this observable object and the {@code arg} argument.
	 *
	 * @param   arg   any object.
	 */
	public void notifyObservers(Object arg) {
		synchronized (this) {
			/* We don't want the Observer doing callbacks into
			 * arbitrary code while holding its own Monitor.
			 * The code where we extract each Observable from
			 * the Vector and store the state of the Observer
			 * needs synchronization, but notifying observers
			 * does not (should not).  The worst result of any
			 * potential race-condition here is that:
			 * 1) a newly-added Observer will miss a
			 *   notification in progress
			 * 2) a recently unregistered Observer will be
			 *   wrongly notified when it doesn't care
			 */
			if (!changed) {
				return;
			}
			clearChanged();
			if (!alreadyAddObserver) {
				ThreadUtils.objectWait(this);
			}
		}

		for (Observer observer : obs) {
			observer.update(this, arg);
		}
	}

	/**
	 * Clears the observer list so that this object no longer has any observers.
	 */
	public void deleteObservers() {
		obs.clear();
	}

	/**
	 * Marks this {@code Observable} object as having been changed; the
	 * {@code hasChanged} method will now return {@code true}.
	 */
	protected synchronized void setChanged() {
		changed = true;
	}

	/**
	 * Indicates that this object has no longer changed, or that it has
	 * already notified all of its observers of its most recent change,
	 * so that the {@code hasChanged} method will now return {@code false}.
	 * This method is called automatically by the
	 * {@code notifyObservers} methods.
	 *
	 * @see     java.util.Observable#notifyObservers()
	 * @see     java.util.Observable#notifyObservers(java.lang.Object)
	 */
	protected synchronized void clearChanged() {
		changed = false;
	}

	/**
	 * Tests if this object has changed.
	 *
	 * @return  {@code true} if and only if the {@code setChanged}
	 *          method has been called more recently than the
	 *          {@code clearChanged} method on this object;
	 *          {@code false} otherwise.
	 */
	public synchronized boolean hasChanged() {
		return changed;
	}

	/**
	 * Returns the number of observers of this {@code Observable} object.
	 *
	 * @return  the number of observers of this object.
	 */
	public int countObservers() {
		return observerCnt;
	}


}