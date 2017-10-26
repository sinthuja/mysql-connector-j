/*
  Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.

  The MySQL Connector/J is licensed under the terms of the GPLv2
  <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most MySQL Connectors.
  There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
  this software, see the FOSS License Exception
  <http://www.mysql.com/about/legal/licensing/foss-exception.html>.

  This program is free software; you can redistribute it and/or modify it under the terms
  of the GNU General Public License as published by the Free Software Foundation; version 2
  of the License.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along with this
  program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
  Floor, Boston, MA 02110-1301  USA

 */

package com.mysql.cj.mysqla;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mysql.cj.api.Session;
import com.mysql.cj.core.Messages;
import com.mysql.cj.core.MysqlType;
import com.mysql.cj.core.conf.PropertyDefinitions;
import com.mysql.cj.core.exceptions.ExceptionFactory;
import com.mysql.cj.core.exceptions.WrongArgumentException;
import com.mysql.cj.core.util.StringUtils;
import com.mysql.cj.core.util.TimeUtil;

public class ServerPreparedQueryBindings extends AbstractQueryBindings<ServerPreparedQueryBindValue> {

    /** Do we need to send/resend types to the server? */
    private AtomicBoolean sendTypesToServer = new AtomicBoolean(false);

    /**
     * Flag indicating whether or not the long parameters have been 'switched'
     * back to normal parameters. We can not execute() if clearParameters()
     * hasn't been called in this case.
     */
    private boolean longParameterSwitchDetected = false;

    public ServerPreparedQueryBindings(int parameterCount, Session sess) {
        super(parameterCount, sess);
    }

    @Override
    protected void initBindValues(int parameterCount) {
        this.bindValues = new ServerPreparedQueryBindValue[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            this.bindValues[i] = new ServerPreparedQueryBindValue();
        }
    }

    @Override
    public ServerPreparedQueryBindings clone() {
        ServerPreparedQueryBindings newBindings = new ServerPreparedQueryBindings(this.bindValues.length, this.session);
        ServerPreparedQueryBindValue[] bvs = new ServerPreparedQueryBindValue[this.bindValues.length];
        for (int i = 0; i < this.bindValues.length; i++) {
            bvs[i] = this.bindValues[i].clone();
        }
        newBindings.bindValues = bvs;
        newBindings.sendTypesToServer = this.sendTypesToServer;
        newBindings.longParameterSwitchDetected = this.longParameterSwitchDetected;
        newBindings.isLoadDataQuery = this.isLoadDataQuery;
        return newBindings;
    }

    /**
     * Returns the structure representing the value that (can be)/(is)
     * bound at the given parameter index.
     * 
     * @param parameterIndex
     *            0-based
     * @param forLongData
     *            is this for a stream?
     */
    public ServerPreparedQueryBindValue getBinding(int parameterIndex, boolean forLongData) {

        if (this.bindValues[parameterIndex] == null) {
            //            this.bindValues[parameterIndex] = new ServerPreparedQueryBindValue();
        } else {
            if (this.bindValues[parameterIndex].isLongData && !forLongData) {
                this.longParameterSwitchDetected = true;
            }
        }

        return this.bindValues[parameterIndex];
    }

    @Override
    public void checkParameterSet(int columnIndex) {
        if (!this.bindValues[columnIndex].isSet()) {
            throw ExceptionFactory.createException(WrongArgumentException.class,
                    Messages.getString("ServerPreparedStatement.13") + (columnIndex + 1) + Messages.getString("ServerPreparedStatement.14"));
        }
    }

    public AtomicBoolean getSendTypesToServer() {
        return this.sendTypesToServer;
    }

    public boolean isLongParameterSwitchDetected() {
        return this.longParameterSwitchDetected;
    }

    public void setLongParameterSwitchDetected(boolean longParameterSwitchDetected) {
        this.longParameterSwitchDetected = longParameterSwitchDetected;
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) {
        setAsciiStream(parameterIndex, x, -1);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) {
        if (x == null) {
            setNull(parameterIndex);
        } else {
            ServerPreparedQueryBindValue binding = getBinding(parameterIndex, true);
            this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_BLOB, this.numberOfExecutions));
            binding.value = x;
            binding.isLongData = true;
            binding.bindLength = this.useStreamLengthsInPrepStmts.getValue() ? length : -1;
        }
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) {
        setAsciiStream(parameterIndex, x, (int) length);
        this.bindValues[parameterIndex].setMysqlType(MysqlType.TEXT); // TODO was Types.CLOB, check; use length to find right TEXT type
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) {
        if (x == null) {
            setNull(parameterIndex);
        } else {
            ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
            this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_NEWDECIMAL, this.numberOfExecutions));
            binding.value = StringUtils.fixDecimalExponent(x.toPlainString());
        }
    }

    @Override
    public void setBigInteger(int parameterIndex, BigInteger x) {
        setValue(parameterIndex, x.toString(), MysqlType.BIGINT_UNSIGNED);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) {
        setBinaryStream(parameterIndex, x, -1);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) {
        if (x == null) {
            setNull(parameterIndex);
        } else {
            ServerPreparedQueryBindValue binding = getBinding(parameterIndex, true);
            this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_BLOB, this.numberOfExecutions));
            binding.value = x;
            binding.isLongData = true;
            binding.bindLength = this.useStreamLengthsInPrepStmts.getValue() ? length : -1;
        }
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) {
        setBinaryStream(parameterIndex, x, (int) length);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) {
        setBinaryStream(parameterIndex, inputStream);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) {
        setBinaryStream(parameterIndex, inputStream, (int) length);
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) {

        if (x == null) {
            setNull(parameterIndex);
        } else {
            try {
                ServerPreparedQueryBindValue binding = getBinding(parameterIndex, true);
                this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_BLOB, this.numberOfExecutions));
                binding.value = x;
                binding.isLongData = true;
                binding.bindLength = this.useStreamLengthsInPrepStmts.getValue() ? x.length() : -1;
            } catch (Throwable t) {
                throw ExceptionFactory.createException(t.getMessage(), t);
            }
        }
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) {
        setByte(parameterIndex, (x ? (byte) 1 : (byte) 0));
    }

    @Override
    public void setByte(int parameterIndex, byte x) {
        ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
        this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_TINY, this.numberOfExecutions));
        binding.longBinding = x;
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) {
        if (x == null) {
            setNull(parameterIndex);
        } else {
            ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
            this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_VAR_STRING, this.numberOfExecutions));
            binding.value = x;
        }
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x, boolean checkForIntroducer, boolean escapeForMBChars) {
        setBytes(parameterIndex, x);
    }

    @Override
    public void setBytesNoEscape(int parameterIndex, byte[] parameterAsBytes) {
        byte[] parameterWithQuotes = new byte[parameterAsBytes.length + 2];
        parameterWithQuotes[0] = '\'';
        System.arraycopy(parameterAsBytes, 0, parameterWithQuotes, 1, parameterAsBytes.length);
        parameterWithQuotes[parameterAsBytes.length + 1] = '\'';

        setValue(parameterIndex, parameterWithQuotes);
    }

    @Override
    public void setBytesNoEscapeNoQuotes(int parameterIndex, byte[] parameterAsBytes) {
        setBytes(parameterIndex, parameterAsBytes);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) {
        setCharacterStream(parameterIndex, reader, -1);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) {
        if (reader == null) {
            setNull(parameterIndex);
        } else {
            ServerPreparedQueryBindValue binding = getBinding(parameterIndex, true);
            this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_BLOB, this.numberOfExecutions));
            binding.value = reader;
            binding.isLongData = true;
            binding.bindLength = this.useStreamLengthsInPrepStmts.getValue() ? length : -1;
        }
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) {
        setCharacterStream(parameterIndex, reader, (int) length);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) {
        setCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) {
        setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setClob(int parameterIndex, Clob x) {
        if (x == null) {
            setNull(parameterIndex);
        } else {
            try {
                ServerPreparedQueryBindValue binding = getBinding(parameterIndex, true);
                this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_BLOB, this.numberOfExecutions));
                binding.value = x.getCharacterStream();
                binding.isLongData = true;
                binding.bindLength = this.useStreamLengthsInPrepStmts.getValue() ? x.length() : -1;
            } catch (Throwable t) {
                throw ExceptionFactory.createException(t.getMessage(), t);
            }
        }
    }

    @Override
    public void setDate(int parameterIndex, Date x) {
        setDate(parameterIndex, x, this.session.getDefaultTimeZone());
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) {
        setDate(parameterIndex, x, cal.getTimeZone());
    }

    @Override
    public void setDate(int parameterIndex, Date x, TimeZone tz) {
        if (x == null) {
            setNull(parameterIndex);
        } else {
            ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
            this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_DATE, this.numberOfExecutions));
            binding.value = x;
            binding.tz = tz;
        }
    }

    @Override
    public void setDouble(int parameterIndex, double x) {
        if (!this.session.getPropertySet().getBooleanReadableProperty(PropertyDefinitions.PNAME_allowNanAndInf).getValue()
                && (x == Double.POSITIVE_INFINITY || x == Double.NEGATIVE_INFINITY || Double.isNaN(x))) {
            throw ExceptionFactory.createException(WrongArgumentException.class, Messages.getString("PreparedStatement.64", new Object[] { x }),
                    this.session.getExceptionInterceptor());
        }
        ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
        this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_DOUBLE, this.numberOfExecutions));
        binding.doubleBinding = x;
    }

    @Override
    public void setFloat(int parameterIndex, float x) {
        ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
        this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_FLOAT, this.numberOfExecutions));
        binding.floatBinding = x;
    }

    @Override
    public void setInt(int parameterIndex, int x) {
        ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
        this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_LONG, this.numberOfExecutions));
        binding.longBinding = x;
    }

    @Override
    public void setLong(int parameterIndex, long x) {
        ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
        this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_LONGLONG, this.numberOfExecutions));
        binding.longBinding = x;
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) {
        setNCharacterStream(parameterIndex, value, -1);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader reader, long length) {
        if (!this.charEncoding.equalsIgnoreCase("UTF-8") && !this.charEncoding.equalsIgnoreCase("utf8")) {
            throw ExceptionFactory.createException(Messages.getString("ServerPreparedStatement.28"), this.session.getExceptionInterceptor());
        }

        if (reader == null) {
            setNull(parameterIndex);
        } else {
            ServerPreparedQueryBindValue binding = getBinding(parameterIndex, true);
            this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_BLOB, this.numberOfExecutions));
            binding.value = reader;
            binding.isLongData = true;
            binding.bindLength = this.useStreamLengthsInPrepStmts.getValue() ? length : -1;
        }
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) {
        setNCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) {
        if (!this.charEncoding.equalsIgnoreCase("UTF-8") && !this.charEncoding.equalsIgnoreCase("utf8")) {
            throw ExceptionFactory.createException(Messages.getString("ServerPreparedStatement.29"), this.session.getExceptionInterceptor());
        }
        setNCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) {
        try {
            setNClob(parameterIndex, value.getCharacterStream(), this.useStreamLengthsInPrepStmts.getValue() ? value.length() : -1);
        } catch (Throwable t) {
            throw ExceptionFactory.createException(t.getMessage(), t, this.session.getExceptionInterceptor());
        }
    }

    @Override
    public void setNString(int parameterIndex, String x) {
        if (this.charEncoding.equalsIgnoreCase("UTF-8") || this.charEncoding.equalsIgnoreCase("utf8")) {
            setString(parameterIndex, x);
        } else {
            throw ExceptionFactory.createException(Messages.getString("ServerPreparedStatement.30"), this.session.getExceptionInterceptor());
        }
    }

    @Override
    public void setNull(int parameterIndex) {
        ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
        this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_NULL, this.numberOfExecutions));
        binding.setNull(true);
    }

    @Override
    public void setShort(int parameterIndex, short x) {
        ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
        this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_SHORT, this.numberOfExecutions));
        binding.longBinding = x;
    }

    @Override
    public void setString(int parameterIndex, String x) {
        if (x == null) {
            setNull(parameterIndex);
        } else {
            ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
            this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_VAR_STRING, this.numberOfExecutions));
            binding.value = x;
        }
    }

    public void setTime(int parameterIndex, Time x, Calendar cal) {
        setTime(parameterIndex, x, cal.getTimeZone());
    }

    public void setTime(int parameterIndex, Time x) {
        setTime(parameterIndex, x, this.session.getDefaultTimeZone());
    }

    @Override
    public void setTime(int parameterIndex, Time x, TimeZone tz) {
        if (x == null) {
            setNull(parameterIndex);
        } else {
            ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
            this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_TIME, this.numberOfExecutions));
            binding.value = x;
            binding.tz = tz;
        }
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) {
        setTimestamp(parameterIndex, x, this.session.getDefaultTimeZone());
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) {
        setTimestamp(parameterIndex, x, cal.getTimeZone());
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, TimeZone tz) {
        if (x == null) {
            setNull(parameterIndex);
        } else {
            ServerPreparedQueryBindValue binding = getBinding(parameterIndex, false);
            this.sendTypesToServer.compareAndSet(false, binding.resetToType(MysqlaConstants.FIELD_TYPE_DATETIME, this.numberOfExecutions));

            if (!this.sendFractionalSeconds.getValue()) {
                x = TimeUtil.truncateFractionalSeconds(x);
            }

            binding.value = x;
            binding.tz = tz;
        }
    }
}
