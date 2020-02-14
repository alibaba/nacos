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

package com.alibaba.nacos.config.server.store;

import org.apache.derby.io.StorageRandomAccessFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NRandomAccessFile extends RandomAccessFile implements StorageRandomAccessFile {

    // for cloning
    private final   File    _name;
    private final   String  _mode;

    public NRandomAccessFile(File name, String mode) throws FileNotFoundException {
        super(name, mode);
        _name = name;
        _mode = mode;
    }

    @Override
    public void sync() throws IOException {
        getFD().sync();
    }

    @Override
    public StorageRandomAccessFile clone() {
        try {
            return new NRandomAccessFile( _name, _mode );
        }
        catch (IOException ioe)
        {
            throw new RuntimeException( ioe.getMessage(), ioe );
        }
    }
}
