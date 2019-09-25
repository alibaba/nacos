package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.nacos.naming.consistency.Datum;

/**
 * @author satjd
 */
public class TransferTask {
    public Datum datum;
    public DatumType datumType;
    public TreePeer source;

    public TransferTask(Datum datum, DatumType datumType, TreePeer source) {
        this.datum = datum;
        this.datumType = datumType;
        this.source = source;
    }
}
