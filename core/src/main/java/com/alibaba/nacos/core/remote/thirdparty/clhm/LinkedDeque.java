/*
 * Copyright 2011 Benjamin Manes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modified by the Nacos project: relocated the package, documented provenance,
 * and replaced optional JCIP annotations with comments. Runtime code is unchanged.
 */
package com.alibaba.nacos.core.remote.thirdparty.clhm;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Copied from SOFA Hessian 3.3.6, originally the concurrentlinkedhashmap
 * implementation by Benjamin Manes, under the Apache License, Version 2.0.
 * Nacos needs this implementation without depending on the entire Hessian jar;
 * this relocated copy is maintained and modified by the Nacos project.
 * See <a href="package-summary.html">the package documentation</a> for the exact source and local changes.
 * <p>
 * Linked list implementation of the the JDK6 Deque interface where the link
 * pointers are tightly integrated with the element. Linked deques have no
 * capacity restrictions; they grow as necessary to support usage. They are not
 * thread-safe; in the absence of external synchronization, they do not support
 * concurrent access by multiple threads. Null elements are prohibited.
 * <p>
 * Most <tt>LinkedDeque</tt> operations run in constant time by assuming that
 * the {@link Linked} parameter is associated with the deque instance. Any usage
 * that violates this assumption will result in non-deterministic behavior.
 * <p>
 * The iterators returned by this class are <em>not</em> <i>fail-fast</i>: If
 * the deque is modified at any time after the iterator is created, the iterator
 * will be in an unknown state. Thus, in the face of concurrent modification,
 * the iterator risks arbitrary, non-deterministic behavior at an undetermined
 * time in the future.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 * @param <E> the type of elements held in this collection
 * @see <a href="http://code.google.com/p/concurrentlinkedhashmap/">
 *      http://code.google.com/p/concurrentlinkedhashmap/</a>
 */
// Not thread-safe.
final class LinkedDeque<E extends Linked<E>> extends AbstractCollection<E> {

    // This class provides a doubly-linked list that is optimized for the virtual
    // machine. The first and last elements are manipulated instead of a slightly
    // more convenient sentinel element to avoid the insertion of null checks with
    // NullPointerException throws in the byte code. The links to a removed
    // element are cleared to help a generational garbage collector if the
    // discarded elements inhabit more than one generation.

    /**
     * Pointer to first node.
     * Invariant: (first == null && last == null) ||
     *            (first.prev == null)
     */
    E   first;

    /**
     * Pointer to last node.
     * Invariant: (first == null && last == null) ||
     *            (last.next == null)
     */
    E   last;

    int size;

    /**
     * Links the element to the front of the deque so that it becomes the first
     * element.
     *
     * @param e the unlinked element
     */
    void linkFirst(final E e) {
        final E f = first;
        first = e;

        if (f == null) {
            last = e;
        } else {
            f.setPrevious(e);
            e.setNext(f);
        }
    }

    /**
     * Links the element to the back of the deque so that it becomes the last
     * element.
     *
     * @param e the unlinked element
     */
    void linkLast(final E e) {
        final E previousLast = last;
        last = e;

        if (previousLast == null) {
            first = e;
        } else {
            previousLast.setNext(e);
            e.setPrevious(previousLast);
        }
    }

    /** Unlinks the non-null first element. */
    E unlinkFirst() {
        final E f = first;
        final E next = f.getNext();
        f.setNext(null);

        first = next;
        if (next == null) {
            last = null;
        } else {
            next.setPrevious(null);
        }
        return f;
    }

    /** Unlinks the non-null last element. */
    E unlinkLast() {
        final E l = last;
        final E prev = l.getPrevious();
        l.setPrevious(null);
        last = prev;
        if (prev == null) {
            first = null;
        } else {
            prev.setNext(null);
        }
        return l;
    }

    /** Unlinks the non-null element. */
    void unlink(E e) {
        final E prev = e.getPrevious();
        final E next = e.getNext();

        if (prev == null) {
            first = next;
        } else {
            prev.setNext(next);
            e.setPrevious(null);
        }

        if (next == null) {
            last = prev;
        } else {
            next.setPrevious(prev);
            e.setNext(null);
        }
    }

    @Override
    public boolean isEmpty() {
        return (first == null);
    }

    void checkNotEmpty() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        for (E e = first; e != null;) {
            E next = e.getNext();
            e.setPrevious(null);
            e.setNext(null);
            e = next;
        }
        first = last = null;
        size = 0;
    }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof Linked<?>)) {
            return false;
        }
        Linked<?> e = (Linked<?>) o;
        return (e.getPrevious() != null)
            || (e.getNext() != null)
            || (e == first);
    }

    /**
     * Moves the element to the front of the deque so that it becomes the first
     * element.
     *
     * @param e the linked element
     */
    public void moveToFront(E e) {
        if (e != first) {
            unlink(e);
            linkFirst(e);
        }
    }

    /**
     * Moves the element to the back of the deque so that it becomes the last
     * element.
     *
     * @param e the linked element
     */
    public void moveToBack(E e) {
        if (e != last) {
            unlink(e);
            linkLast(e);
        }
    }

    public E peek() {
        return peekFirst();
    }

    public E peekFirst() {
        return first;
    }

    public E peekLast() {
        return last;
    }

    public E getFirst() {
        checkNotEmpty();
        return peekFirst();
    }

    public E getLast() {
        checkNotEmpty();
        return peekLast();
    }

    public E element() {
        return getFirst();
    }

    public boolean offer(E e) {
        return offerLast(e);
    }

    public boolean offerFirst(E e) {
        if (contains(e)) {
            return false;
        }
        size++;
        linkFirst(e);
        return true;
    }

    public boolean offerLast(E e) {
        if (contains(e)) {
            return false;
        }
        size++;
        linkLast(e);
        return true;
    }

    @Override
    public boolean add(E e) {
        return offerLast(e);
    }

    public void addFirst(E e) {
        if (!offerFirst(e)) {
            throw new IllegalArgumentException();
        }
    }

    public void addLast(E e) {
        if (!offerLast(e)) {
            throw new IllegalArgumentException();
        }
    }

    public E poll() {
        return pollFirst();
    }

    public E pollFirst() {
        if (isEmpty()) {
            return null;
        }
        size--;
        return unlinkFirst();
    }

    public E pollLast() {
        if (isEmpty()) {
            return null;
        }
        size--;
        return unlinkLast();
    }

    public E remove() {
        return removeFirst();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean remove(Object o) {
        if (contains(o)) {
            size--;
            unlink((E) o);
            return true;
        }
        return false;
    }

    public E removeFirst() {
        checkNotEmpty();
        return pollFirst();
    }

    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    public E removeLast() {
        checkNotEmpty();
        return pollLast();
    }

    public boolean removeLastOccurrence(Object o) {
        return remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Object o : c) {
            modified |= remove(o);
        }
        return modified;
    }

    public void push(E e) {
        addFirst(e);
    }

    public E pop() {
        return removeFirst();
    }

    @Override
    public Iterator<E> iterator() {
        return new AbstractLinkedIterator(first) {
            @Override
            E computeNext() {
                return cursor.getNext();
            }
        };
    }

    public Iterator<E> descendingIterator() {
        return new AbstractLinkedIterator(last) {
            @Override
            E computeNext() {
                return cursor.getPrevious();
            }
        };
    }

    abstract class AbstractLinkedIterator implements Iterator<E> {
        E cursor;

        /**
         * Creates an iterator that can can traverse the deque.
         *
         * @param start the initial element to begin traversal from
         */
        AbstractLinkedIterator(E start) {
            cursor = start;
        }

        public boolean hasNext() {
            return (cursor != null);
        }

        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            E e = cursor;
            cursor = computeNext();
            return e;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        /**
         * Retrieves the next element to traverse to or <tt>null</tt> if there are
         * no more elements.
         */
        abstract E computeNext();
    }
}

/**
 * An element that is linked on the {@link Deque}.
 */
interface Linked<T extends Linked<T>> {

    /**
     * Retrieves the previous element or <tt>null</tt> if either the element is
     * unlinked or the first element on the deque.
     */
    T getPrevious();

    /** Sets the previous element or <tt>null</tt> if there is no link. */
    void setPrevious(T prev);

    /**
     * Retrieves the next element or <tt>null</tt> if either the element is
     * unlinked or the last element on the deque.
     */
    T getNext();

    /** Sets the next element or <tt>null</tt> if there is no link. */
    void setNext(T next);
}
