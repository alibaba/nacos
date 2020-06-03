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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * copy from <link>org.apache.commons.collections</link>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class CollectionUtils {

	/**
	 * Returns the <code>index</code>-th value in <code>object</code>, throwing
	 * <code>IndexOutOfBoundsException</code> if there is no such element or
	 * <code>IllegalArgumentException</code> if <code>object</code> is not an
	 * instance of one of the supported types.
	 * <p>
	 * The supported types, and associated semantics are:
	 * <ul>
	 * <li> Map -- the value returned is the <code>Map.Entry</code> in position
	 *      <code>index</code> in the map's <code>entrySet</code> iterator,
	 *      if there is such an entry.</li>
	 * <li> List -- this method is equivalent to the list's get method.</li>
	 * <li> Array -- the <code>index</code>-th array entry is returned,
	 *      if there is such an entry; otherwise an <code>IndexOutOfBoundsException</code>
	 *      is thrown.</li>
	 * <li> Collection -- the value returned is the <code>index</code>-th object
	 *      returned by the collection's default iterator, if there is such an element.</li>
	 * <li> Iterator or Enumeration -- the value returned is the
	 *      <code>index</code>-th object in the Iterator/Enumeration, if there
	 *      is such an element.  The Iterator/Enumeration is advanced to
	 *      <code>index</code> (or to the end, if <code>index</code> exceeds the
	 *      number of entries) as a side effect of this method.</li>
	 * </ul>
	 *
	 * @param object  the object to get a value from
	 * @param index  the index to get
	 * @return the object at the specified index
	 * @throws IndexOutOfBoundsException if the index is invalid
	 * @throws IllegalArgumentException if the object type is invalid
	 */
	public static Object get(Object object, int index) {
		if (index < 0) {
			throw new IndexOutOfBoundsException("Index cannot be negative: " + index);
		}
		if (object instanceof Map) {
			Map map = (Map) object;
			Iterator iterator = map.entrySet().iterator();
			return get(iterator, index);
		} else if (object instanceof List) {
			return ((List) object).get(index);
		} else if (object instanceof Object[]) {
			return ((Object[]) object)[index];
		} else if (object instanceof Iterator) {
			Iterator it = (Iterator) object;
			while (it.hasNext()) {
				index--;
				if (index == -1) {
					return it.next();
				} else {
					it.next();
				}
			}
			throw new IndexOutOfBoundsException("Entry does not exist: " + index);
		} else if (object instanceof Collection) {
			Iterator iterator = ((Collection) object).iterator();
			return get(iterator, index);
		} else if (object instanceof Enumeration) {
			Enumeration it = (Enumeration) object;
			while (it.hasMoreElements()) {
				index--;
				if (index == -1) {
					return it.nextElement();
				} else {
					it.nextElement();
				}
			}
			throw new IndexOutOfBoundsException("Entry does not exist: " + index);
		} else if (object == null) {
			throw new IllegalArgumentException("Unsupported object type: null");
		} else {
			try {
				return Array.get(object, index);
			} catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("Unsupported object type: " + object.getClass().getName());
			}
		}
	}

	/**
	 * Gets the size of the collection/iterator specified.
	 * <p>
	 * This method can handles objects as follows
	 * <ul>
	 * <li>Collection - the collection size
	 * <li>Map - the map size
	 * <li>Array - the array size
	 * <li>Iterator - the number of elements remaining in the iterator
	 * <li>Enumeration - the number of elements remaining in the enumeration
	 * </ul>
	 *
	 * @param object  the object to get the size of
	 * @return the size of the specified collection
	 * @throws IllegalArgumentException thrown if object is not recognised or null
	 * @since Commons Collections 3.1
	 */
	public static int size(Object object) {
		int total = 0;
		if (object instanceof Map) {
			total = ((Map) object).size();
		} else if (object instanceof Collection) {
			total = ((Collection) object).size();
		} else if (object instanceof Object[]) {
			total = ((Object[]) object).length;
		} else if (object instanceof Iterator) {
			Iterator it = (Iterator) object;
			while (it.hasNext()) {
				total++;
				it.next();
			}
		} else if (object instanceof Enumeration) {
			Enumeration it = (Enumeration) object;
			while (it.hasMoreElements()) {
				total++;
				it.nextElement();
			}
		} else if (object == null) {
			throw new IllegalArgumentException("Unsupported object type: null");
		} else {
			try {
				total = Array.getLength(object);
			} catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("Unsupported object type: " + object.getClass().getName());
			}
		}
		return total;
	}

	public static boolean sizeIsEmpty(Object object) {
		if (object instanceof Collection) {
			return ((Collection) object).isEmpty();
		} else if (object instanceof Map) {
			return ((Map) object).isEmpty();
		} else if (object instanceof Object[]) {
			return ((Object[]) object).length == 0;
		} else if (object instanceof Iterator) {
			return ((Iterator) object).hasNext() == false;
		} else if (object instanceof Enumeration) {
			return ((Enumeration) object).hasMoreElements() == false;
		} else if (object == null) {
			throw new IllegalArgumentException("Unsupported object type: null");
		} else {
			try {
				return Array.getLength(object) == 0;
			} catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("Unsupported object type: " + object.getClass().getName());
			}
		}
	}

	/**
	 * Null-safe check if the specified collection is empty.
	 * <p>
	 * Null returns true.
	 *
	 * @param coll  the collection to check, may be null
	 * @return true if empty or null
	 * @since Commons Collections 3.2
	 */
	public static boolean isEmpty(Collection coll) {
		return (coll == null || coll.isEmpty());
	}

	/**
	 * Null-safe check if the specified collection is not empty.
	 * <p>
	 * Null returns false.
	 *
	 * @param coll  the collection to check, may be null
	 * @return true if non-null and non-empty
	 * @since Commons Collections 3.2
	 */
	public static boolean isNotEmpty(Collection coll) {
		return !CollectionUtils.isEmpty(coll);
	}

}
