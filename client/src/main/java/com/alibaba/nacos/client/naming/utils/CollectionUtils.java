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

package com.alibaba.nacos.client.naming.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Provides utility methods and decorators for {@link Collection} instances.
 *
 * @author Rodney Waldhoff
 * @author Paul Jack
 * @author Stephen Colebourne
 * @author Steve Downey
 * @author Herve Quiroz
 * @author Peter KoBek
 * @author Matthew Hawthorne
 * @author Janek Bogucki
 * @author Phil Steitz
 * @author Steven Melzer
 * @author Jon Schewe
 * @author Neil O'Toole
 * @author Stephen Smith
 * @version $Revision: 1713167 $ $Date: 2015-11-07 20:44:03 +0100 (Sat, 07 Nov 2015) $
 * @since Commons Collections 1.0
 */
public class CollectionUtils {
    
    /**
     * Constant to avoid repeated object creation.
     */
    private static final Integer INTEGER_ONE = 1;
    
    /**
     * <code>CollectionUtils</code> should not normally be instantiated.
     */
    public CollectionUtils() {
    }
    
    /**
     * Returns a new {@link Collection} containing <tt><i>a</i> - <i>b</i></tt>. The cardinality of each element
     * <i>e</i> in the returned {@link Collection} will be the cardinality of <i>e</i> in <i>a</i> minus the
     * cardinality of <i>e</i> in <i>b</i>, or zero, whichever is greater.
     *
     * @param a the collection to subtract from, must not be null
     * @param b the collection to subtract, must not be null
     * @return a new collection with the results
     * @see Collection#removeAll
     */
    public static Collection subtract(final Collection a, final Collection b) {
        ArrayList list = new ArrayList(a);
        for (Iterator it = b.iterator(); it.hasNext(); ) {
            list.remove(it.next());
        }
        return list;
    }
    
    /**
     * Returns a {@link Map} mapping each unique element in the given {@link Collection} to an {@link Integer}
     * representing the number of occurrences of that element in the {@link Collection}.
     *
     * <p>Only those elements present in the collection will appear as keys in the map.
     *
     * @param coll the collection to get the cardinality map for, must not be null
     * @return the populated cardinality map
     */
    public static Map getCardinalityMap(final Collection coll) {
        Map count = new HashMap(coll.size());
        for (Iterator it = coll.iterator(); it.hasNext(); ) {
            Object obj = it.next();
            Integer c = (Integer) (count.get(obj));
            if (c == null) {
                count.put(obj, INTEGER_ONE);
            } else {
                count.put(obj, c + 1);
            }
        }
        return count;
    }
    
    /**
     * Returns <tt>true</tt> iff the given {@link Collection}s contain exactly the same elements with exactly the same
     * cardinalities.
     *
     * <p>That is, iff the cardinality of <i>e</i> in <i>a</i> is equal to the cardinality of <i>e</i> in <i>b</i>, for
     * each element <i>e</i> in <i>a</i> or <i>b</i>.
     *
     * @param a the first collection, must not be null
     * @param b the second collection, must not be null
     * @return <code>true</code> iff the collections contain the same elements with the same cardinalities.
     */
    public static boolean isEqualCollection(final Collection a, final Collection b) {
        if (a.size() != b.size()) {
            return false;
        } else {
            Map mapa = getCardinalityMap(a);
            Map mapb = getCardinalityMap(b);
            if (mapa.size() != mapb.size()) {
                return false;
            } else {
                Iterator it = mapa.keySet().iterator();
                while (it.hasNext()) {
                    Object obj = it.next();
                    if (getFreq(obj, mapa) != getFreq(obj, mapb)) {
                        return false;
                    }
                }
                return true;
            }
        }
    }
    
    //-----------------------------------------------------------------------
    
    /**
     * Null-safe check if the specified collection is empty.
     *
     * <p>Null returns true.
     *
     * @param coll the collection to check, may be null
     * @return true if empty or null
     * @since Commons Collections 3.2
     */
    public static boolean isEmpty(Collection coll) {
        return (coll == null || coll.isEmpty());
    }
    
    private static int getFreq(final Object obj, final Map freqMap) {
        Integer count = (Integer) freqMap.get(obj);
        if (count != null) {
            return count.intValue();
        }
        return 0;
    }
}
