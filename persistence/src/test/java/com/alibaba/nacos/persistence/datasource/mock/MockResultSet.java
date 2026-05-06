/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.datasource.mock;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

@SuppressWarnings("checkstyle:OverloadMethodsDeclarationOrder")
public class MockResultSet implements ResultSet {
    
    @Override
    public boolean next() throws SQLException {
        return false;
    }
    
    @Override
    public void close() throws SQLException {
    
    }
    
    @Override
    public boolean wasNull() throws SQLException {
        return false;
    }
    
    @Override
    public String getString(int columnIndex) throws SQLException {
        return "";
    }
    
    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        return false;
    }
    
    @Override
    public byte getByte(int columnIndex) throws SQLException {
        return 0;
    }
    
    @Override
    public short getShort(int columnIndex) throws SQLException {
        return 0;
    }
    
    @Override
    public int getInt(int columnIndex) throws SQLException {
        return 0;
    }
    
    @Override
    public long getLong(int columnIndex) throws SQLException {
        return 0;
    }
    
    @Override
    public float getFloat(int columnIndex) throws SQLException {
        return 0;
    }
    
    @Override
    public double getDouble(int columnIndex) throws SQLException {
        return 0;
    }
    
    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        return null;
    }
    
    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        return new byte[0];
    }
    
    @Override
    public Date getDate(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public Time getTime(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public String getString(String columnLabel) throws SQLException {
        return "";
    }
    
    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        return false;
    }
    
    @Override
    public byte getByte(String columnLabel) throws SQLException {
        return 0;
    }
    
    @Override
    public short getShort(String columnLabel) throws SQLException {
        return 0;
    }
    
    @Override
    public int getInt(String columnLabel) throws SQLException {
        return 0;
    }
    
    @Override
    public long getLong(String columnLabel) throws SQLException {
        return 0;
    }
    
    @Override
    public float getFloat(String columnLabel) throws SQLException {
        return 0;
    }
    
    @Override
    public double getDouble(String columnLabel) throws SQLException {
        return 0;
    }
    
    @Override
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        return null;
    }
    
    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        return new byte[0];
    }
    
    @Override
    public Date getDate(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public Time getTime(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public InputStream getAsciiStream(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public InputStream getUnicodeStream(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }
    
    @Override
    public void clearWarnings() throws SQLException {
    
    }
    
    @Override
    public String getCursorName() throws SQLException {
        return "";
    }
    
    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return null;
    }
    
    @Override
    public Object getObject(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public int findColumn(String columnLabel) throws SQLException {
        return 0;
    }
    
    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public boolean isBeforeFirst() throws SQLException {
        return false;
    }
    
    @Override
    public boolean isAfterLast() throws SQLException {
        return false;
    }
    
    @Override
    public boolean isFirst() throws SQLException {
        return false;
    }
    
    @Override
    public boolean isLast() throws SQLException {
        return false;
    }
    
    @Override
    public void beforeFirst() throws SQLException {
    
    }
    
    @Override
    public void afterLast() throws SQLException {
    
    }
    
    @Override
    public boolean first() throws SQLException {
        return false;
    }
    
    @Override
    public boolean last() throws SQLException {
        return false;
    }
    
    @Override
    public int getRow() throws SQLException {
        return 0;
    }
    
    @Override
    public boolean absolute(int row) throws SQLException {
        return false;
    }
    
    @Override
    public boolean relative(int rows) throws SQLException {
        return false;
    }
    
    @Override
    public boolean previous() throws SQLException {
        return false;
    }
    
    @Override
    public void setFetchDirection(int direction) throws SQLException {
    
    }
    
    @Override
    public int getFetchDirection() throws SQLException {
        return 0;
    }
    
    @Override
    public void setFetchSize(int rows) throws SQLException {
    
    }
    
    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }
    
    @Override
    public int getType() throws SQLException {
        return 0;
    }
    
    @Override
    public int getConcurrency() throws SQLException {
        return 0;
    }
    
    @Override
    public boolean rowUpdated() throws SQLException {
        return false;
    }
    
    @Override
    public boolean rowInserted() throws SQLException {
        return false;
    }
    
    @Override
    public boolean rowDeleted() throws SQLException {
        return false;
    }
    
    @Override
    public void updateNull(int columnIndex) throws SQLException {
    
    }
    
    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
    
    }
    
    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException {
    
    }
    
    @Override
    public void updateShort(int columnIndex, short x) throws SQLException {
    
    }
    
    @Override
    public void updateInt(int columnIndex, int x) throws SQLException {
    
    }
    
    @Override
    public void updateLong(int columnIndex, long x) throws SQLException {
    
    }
    
    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException {
    
    }
    
    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException {
    
    }
    
    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
    
    }
    
    @Override
    public void updateString(int columnIndex, String x) throws SQLException {
    
    }
    
    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
    
    }
    
    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException {
    
    }
    
    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException {
    
    }
    
    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
    
    }
    
    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
    
    }
    
    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
    
    }
    
    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
    
    }
    
    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
    
    }
    
    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException {
    
    }
    
    @Override
    public void updateNull(String columnLabel) throws SQLException {
    
    }
    
    @Override
    public void updateBoolean(String columnLabel, boolean x) throws SQLException {
    
    }
    
    @Override
    public void updateByte(String columnLabel, byte x) throws SQLException {
    
    }
    
    @Override
    public void updateShort(String columnLabel, short x) throws SQLException {
    
    }
    
    @Override
    public void updateInt(String columnLabel, int x) throws SQLException {
    
    }
    
    @Override
    public void updateLong(String columnLabel, long x) throws SQLException {
    
    }
    
    @Override
    public void updateFloat(String columnLabel, float x) throws SQLException {
    
    }
    
    @Override
    public void updateDouble(String columnLabel, double x) throws SQLException {
    
    }
    
    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
    
    }
    
    @Override
    public void updateString(String columnLabel, String x) throws SQLException {
    
    }
    
    @Override
    public void updateBytes(String columnLabel, byte[] x) throws SQLException {
    
    }
    
    @Override
    public void updateDate(String columnLabel, Date x) throws SQLException {
    
    }
    
    @Override
    public void updateTime(String columnLabel, Time x) throws SQLException {
    
    }
    
    @Override
    public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
    
    }
    
    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
    
    }
    
    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
    
    }
    
    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
    
    }
    
    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
    
    }
    
    @Override
    public void updateObject(String columnLabel, Object x) throws SQLException {
    
    }
    
    @Override
    public void insertRow() throws SQLException {
    
    }
    
    @Override
    public void updateRow() throws SQLException {
    
    }
    
    @Override
    public void deleteRow() throws SQLException {
    
    }
    
    @Override
    public void refreshRow() throws SQLException {
    
    }
    
    @Override
    public void cancelRowUpdates() throws SQLException {
    
    }
    
    @Override
    public void moveToInsertRow() throws SQLException {
    
    }
    
    @Override
    public void moveToCurrentRow() throws SQLException {
    
    }
    
    @Override
    public Statement getStatement() throws SQLException {
        return null;
    }
    
    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        return null;
    }
    
    @Override
    public Ref getRef(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public Blob getBlob(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public Clob getClob(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public Array getArray(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        return null;
    }
    
    @Override
    public Ref getRef(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public Blob getBlob(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public Clob getClob(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public Array getArray(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        return null;
    }
    
    @Override
    public Date getDate(String columnLabel, Calendar cal) throws SQLException {
        return null;
    }
    
    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        return null;
    }
    
    @Override
    public Time getTime(String columnLabel, Calendar cal) throws SQLException {
        return null;
    }
    
    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        return null;
    }
    
    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        return null;
    }
    
    @Override
    public URL getURL(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public URL getURL(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException {
    
    }
    
    @Override
    public void updateRef(String columnLabel, Ref x) throws SQLException {
    
    }
    
    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException {
    
    }
    
    @Override
    public void updateBlob(String columnLabel, Blob x) throws SQLException {
    
    }
    
    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException {
    
    }
    
    @Override
    public void updateClob(String columnLabel, Clob x) throws SQLException {
    
    }
    
    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException {
    
    }
    
    @Override
    public void updateArray(String columnLabel, Array x) throws SQLException {
    
    }
    
    @Override
    public RowId getRowId(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public RowId getRowId(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
    
    }
    
    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException {
    
    }
    
    @Override
    public int getHoldability() throws SQLException {
        return 0;
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }
    
    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException {
    
    }
    
    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException {
    
    }
    
    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
    
    }
    
    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
    
    }
    
    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public NClob getNClob(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
    
    }
    
    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
    
    }
    
    @Override
    public String getNString(int columnIndex) throws SQLException {
        return "";
    }
    
    @Override
    public String getNString(String columnLabel) throws SQLException {
        return "";
    }
    
    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        return null;
    }
    
    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException {
        return null;
    }
    
    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    
    }
    
    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
    
    }
    
    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
    
    }
    
    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
    
    }
    
    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    
    }
    
    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
    
    }
    
    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
    
    }
    
    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
    
    }
    
    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
    
    }
    
    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
    
    }
    
    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
    
    }
    
    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
    
    }
    
    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
    
    }
    
    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
    
    }
    
    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
    
    }
    
    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
    
    }
    
    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
    
    }
    
    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
    
    }
    
    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
    
    }
    
    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
    
    }
    
    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
    
    }
    
    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
    
    }
    
    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
    
    }
    
    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
    
    }
    
    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException {
    
    }
    
    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException {
    
    }
    
    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
    
    }
    
    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
    
    }
    
    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        return null;
    }
    
    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        return null;
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }
}
