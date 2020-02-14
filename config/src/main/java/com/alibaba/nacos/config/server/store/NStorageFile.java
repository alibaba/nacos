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

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.io.StorageFile;
import org.apache.derby.io.StorageRandomAccessFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NStorageFile extends File implements StorageFile {

    public NStorageFile(String pathname) {
        super(pathname);
    }

    public NStorageFile(String parent, String child) {
        super(parent, child);
    }

    public NStorageFile(File parent, String child) {
        super(parent, child);
    }

    public NStorageFile(URI uri) {
        super(uri);
    }

    @Override
    public boolean deleteAll() {
        return false;
    }

    @Override
    public boolean renameTo(StorageFile storageFile) {
        return false;
    }

    @Override
    public StorageFile getParentDir() {
        return null;
    }

    @Override
    public OutputStream getOutputStream() throws FileNotFoundException {
        return null;
    }

    @Override
    public OutputStream getOutputStream(boolean b) throws FileNotFoundException {
        return null;
    }

    @Override
    public InputStream getInputStream() throws FileNotFoundException {
        return null;
    }

    @Override
    public int getExclusiveFileLock() throws StandardException {
        return 0;
    }

    @Override
    public void releaseExclusiveFileLock() {

    }

    @Override
    public StorageRandomAccessFile getRandomAccessFile(String mode) throws FileNotFoundException {
        return null;
    }

    @Override
    public void limitAccessToOwner() throws IOException {

    }
}
