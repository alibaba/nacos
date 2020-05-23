package com.alibaba.nacos.config.server.utils;

import java.util.Arrays;

/**
 * @author common-lang3
 */
public class MultipartKey {

    private final Object[] keys;
    private int hashCode;

    /**
     * Constructs an instance of <code>MultipartKey</code> to hold the specified objects.
     * @param keys the set of objects that make up the key.  Each key may be null.
     */
    public MultipartKey(final Object... keys) {
        this.keys = keys;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        // Eliminate the usual boilerplate because
        // this inner static class is only used in a generic ConcurrentHashMap
        // which will not compare against other Object types
        return Arrays.equals(keys, ((MultipartKey)obj).keys);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        if(hashCode==0) {
            int rc= 0;
            for(final Object key : keys) {
                if(key!=null) {
                    rc= rc*7 + key.hashCode();
                }
            }
            hashCode= rc;
        }
        return hashCode;
    }
}
