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

import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.request.GetRequest;
import org.apache.derby.io.StorageFile;
import org.apache.derby.io.WritableStorageFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.SyncFailedException;

/**
 * Data storage implementation under derby cluster inside nacos
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NStorageFactory implements WritableStorageFactory, LogProcessor4CP {

    private static ConsistencyProtocol<? extends Config> protocol;


    /**
     * Classes implementing the StorageFactory interface must have a null
     * constructor.  The init method is called when the database is booted up to
     * initialize the class. It should perform all actions necessary to start the
     * basic storage, such as creating a temporary file directory.
     * <p>
     * This method should not create the database directory.
     * <p>
     * The init method will be called once, before any other method is called, and will not
     * be called again.
     *
     * @param home         The name of the directory containing the database. It comes from the system.home system property.
     *                     It may be null. A storage factory may decide to ignore this parameter. (For instance the classpath
     *                     storage factory ignores it).
     * @param databaseName The name of the database (directory). The name does not include the subsubprotocol.
     *                     If null then the storage factory will only be used to deal with the directory containing
     *                     the databases.
     * @param tempDirName  The name of the temporary file directory set in properties. If null then a default
     *                     directory should be used. Each database should get a separate temporary file
     *                     directory within this one to avoid collisions.
     * @param uniqueName   A unique name that can be used to create the temporary file directory for this database.
     *                     If null then temporary files will not be created in this StorageFactory instance, and the
     *                     temporary file directory should not be created.
     * @throws IOException
     */
    @Override
    public void init(String home, String databaseName, String tempDirName, String uniqueName)
            throws IOException {

    }

    /**
     * The shutdown method is called during the normal shutdown of the database. However, the database
     * engine cannot guarantee that shutdown will be called. If the JVM terminates abnormally then it will
     * not be called.
     */
    @Override
    public void shutdown() {

    }

    /**
     * Force the data of an output stream out to the underlying storage. That is, ensure that
     * it has been made persistent. If the database is to be transient, that is, if the database
     * does not survive a restart, then the sync method implementation need not do anything.
     *
     * @param stream   The stream to be synchronized.
     * @param metaData If true then this method must force both changes to the file's
     *                 contents and metadata to be written to storage; if false, it need only force file content changes
     *                 to be written. The implementation is allowed to ignore this parameter and always force out
     *                 metadata changes.
     * @throws IOException         if an I/O error occurs.
     * @throws SyncFailedException Thrown when the buffers cannot be flushed,
     *                             or because the system cannot guarantee that all the buffers have been
     *                             synchronized with physical media.
     */
    @Override
    public void sync(OutputStream stream, boolean metaData) throws IOException, SyncFailedException {

    }

    /**
     * This method tests whether the StorageRandomAccessFile "rws" and "rwd" modes
     * are implemented. If the "rws" and "rwd" modes are supported then the database
     * engine will conclude that the write methods of "rws"/"rwd" mode
     * StorageRandomAccessFiles are slow but the sync method is fast and optimize
     * accordingly.
     *
     * @return <b>true</b> if an StIRandomAccess file opened with "rws" or "rwd" modes immediately writes data to the
     * underlying storage, <b>false</b> if not.
     */
    @Override
    public boolean supportsWriteSync() {
        return false;
    }

    /**
     * Get the canonical name of the database. This is a name that uniquely identifies it. It is system dependent.
     * <p>
     * The normal, disk based implementation uses method java.io.File.getCanonicalPath on the directory holding the
     * database to construct the canonical name.
     *
     * @return the canonical name
     * @throws IOException if an IO error occurred during the construction of the name.
     */
    @Override
    public String getCanonicalName() throws IOException {
        return null;
    }

    /**
     * Construct a StorageFile from a path name.
     *
     * @param path The path name of the file. If null then return the database directory.
     *             If this parameter denotes the temp directory or a directory under the temp
     *             directory then the resulting StorageFile denotes a temporary file. Otherwise
     *             the path must be relative to the database and the resulting StorageFile denotes a
     *             regular database file (non-temporary).
     * @return A corresponding StorageFile object
     */
    @Override
    public StorageFile newStorageFile(String path) {
        return null;
    }

    /**
     * Construct a non-temporary StorageFile from a directory and file name.
     *
     * @param directoryName The directory part of the path name. If this parameter denotes the
     *                      temp directory or a directory under the temp directory then the resulting
     *                      StorageFile denotes a temporary file. Otherwise the directory name must be
     *                      relative to the database and the resulting StorageFile denotes a
     *                      regular database file (non-temporary).
     * @param fileName      The name of the file within the directory.
     * @return A corresponding StorageFile object
     */
    @Override
    public StorageFile newStorageFile(String directoryName, String fileName) {
        return null;
    }

    /**
     * Construct a StorageFile from a directory and file name. The StorageFile may denote a temporary file
     * or a non-temporary database file, depending upon the directoryName parameter.
     *
     * @param directoryName The directory part of the path name. If this parameter denotes the
     *                      temp directory or a directory under the temp directory then the resulting
     *                      StorageFile denotes a temporary file. Otherwise the resulting StorageFile denotes a
     *                      regular database file (non-temporary).
     * @param fileName      The name of the file within the directory.
     * @return A corresponding StorageFile object
     */
    @Override
    public StorageFile newStorageFile(StorageFile directoryName, String fileName) {
        return null;
    }

    /**
     * Get the pathname separator character used by the StorageFile implementation. This is the
     * separator that must be used in directory and file name strings.
     *
     * @return the pathname separator character. (Normally '/' or '\').
     */
    @Override
    public char getSeparator() {
        return File.separatorChar;
    }

    /**
     * Get the abstract name of the directory that holds temporary files.
     * <p>
     * The StorageFactory implementation
     * is not required to make temporary files persistent. That is, files created in the temp directory are
     * not required to survive a shutdown of the database engine.
     * <p>
     * However, files created in the temp directory must be writable, <b>even if the database is
     * otherwise read-only</b>.
     *
     * @return a directory name
     */
    @Override
    public StorageFile getTempDir() {
        return null;
    }

    /**
     * This method is used to determine whether the storage is fast (RAM based) or slow (disk based).
     * It may be used by the database engine to determine the default size of the page cache.
     *
     * @return <b>true</b> if the storage is fast, <b>false</b> if it is slow.
     */
    @Override
    public boolean isFast() {
        return false;
    }

    /**
     * Determine whether the database is read only. The database engine supports read-only databases, even
     * in file systems that are writable.
     *
     * @return <b>true</b> if the storage is read only, <b>false</b> if it is writable.
     */
    @Override
    public boolean isReadOnlyDatabase() {
        return false;
    }

    /**
     * Determine whether the storage supports random access. If random access is not supported then
     * it will only be accessed using InputStreams and OutputStreams (if the database is writable).
     *
     * @return <b>true</b> if the storage supports random access, <b>false</b> if it is writable.
     */
    @Override
    public boolean supportsRandomAccess() {
        return false;
    }

    /**
     * The version number of this version of the StorageFactory interface and its subsidiary interfaces.
     */
    int VERSION_NUMBER = 1;

    /**
     * @return the StorageFactory version supported by this implementation
     */
    @Override
    public int getStorageFactoryVersion() {
        return 0;
    }

    /**
     * Create and returns a temporary file in temporary file system of database
     *
     * @param prefix String to prefix the random name generator. It can be null
     * @param suffix String to suffix the random name generator. ".tmp" will be
     *               used if null.
     * @return StorageFile
     */
    @Override
    public StorageFile createTemporaryFile(String prefix, String suffix)
            throws IOException {
        return null;
    }

    /**
     * Set the canonicalName. May need adjustment due to DERBY-5096
     *
     * @param name uniquely identifiable name for this database
     */
    @Override
    public void setCanonicalName(String name) {

    }

    @Override
    public void injectProtocol(ConsistencyProtocol<? extends Config> protocol) {
        NStorageFactory.protocol = protocol;
    }

    @Override
    public <T> T getData(GetRequest request) {
        return null;
    }

    @Override
    public boolean onApply(Log log) {
        return false;
    }

    @Override
    public String bizInfo() {
        return null;
    }

    @Override
    public boolean interest(String key) {
        return false;
    }
}
