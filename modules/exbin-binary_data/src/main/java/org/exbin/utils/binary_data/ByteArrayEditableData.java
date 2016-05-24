/*
 * Copyright (C) ExBin Project
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
package org.exbin.utils.binary_data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Basic implementation of editable binary data interface using byte array.
 *
 * @version 0.1.0 2016/05/24
 * @author ExBin Project (http://exbin.org)
 */
public class ByteArrayEditableData extends ByteArrayData implements EditableBinaryData {

    public ByteArrayEditableData() {
        this(new byte[0]);
    }

    public ByteArrayEditableData(byte[] data) {
        super(data);
    }

    @Override
    public void setDataSize(long size) {
        if (data.length != size) {
            if (size < data.length) {
                data = Arrays.copyOfRange(data, 0, (int) size);
            } else {
                byte[] newData = new byte[(int) size];
                System.arraycopy(data, 0, newData, 0, data.length);
                data = newData;
            }
        }
    }

    @Override
    public void setByte(long position, byte value) {
        try {
            data[(int) position] = value;
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new OutOfBoundsException(ex);
        }
    }

    @Override
    public void insertUninitialized(long startFrom, long length) {
        if (startFrom > data.length) {
            throw new IndexOutOfBoundsException("Data can be inserted only inside or at the end");
        }
        if (length > 0) {
            byte[] newData = new byte[(int) (data.length + length)];
            System.arraycopy(data, 0, newData, 0, (int) startFrom);
            System.arraycopy(data, (int) (startFrom), newData, (int) (startFrom + length), (int) (data.length - startFrom));
            data = newData;
        }
    }

    @Override
    public void insert(long startFrom, long length) {
        if (startFrom > data.length) {
            throw new IndexOutOfBoundsException("Data can be inserted only inside or at the end");
        }
        if (length > 0) {
            byte[] newData = new byte[(int) (data.length + length)];
            System.arraycopy(data, 0, newData, 0, (int) startFrom);
            System.arraycopy(data, (int) (startFrom), newData, (int) (startFrom + length), (int) (data.length - startFrom));
            data = newData;
        }
    }

    @Override
    public void insert(long startFrom, byte[] insertedData) {
        if (startFrom > data.length) {
            throw new IndexOutOfBoundsException("Data can be inserted only inside or at the end");
        }
        int length = insertedData.length;
        if (length > 0) {
            byte[] newData = new byte[(int) (data.length + length)];
            System.arraycopy(data, 0, newData, 0, (int) startFrom);
            System.arraycopy(insertedData, 0, newData, (int) startFrom, length);
            System.arraycopy(data, (int) (startFrom), newData, (int) (startFrom + length), (int) (data.length - startFrom));
            data = newData;
        }
    }

    @Override
    public void insert(long startFrom, byte[] insertedData, int insertedDataOffset, int length) {
        if (startFrom > data.length) {
            throw new IndexOutOfBoundsException("Data can be inserted only inside or at the end");
        }
        if (length > 0) {
            byte[] newData = new byte[(int) (data.length + length)];
            System.arraycopy(data, 0, newData, 0, (int) startFrom);
            System.arraycopy(insertedData, insertedDataOffset, newData, (int) startFrom, length);
            System.arraycopy(data, (int) (startFrom), newData, (int) (startFrom + length), (int) (data.length - startFrom));
            data = newData;
        }
    }

    @Override
    public void insert(long startFrom, BinaryData insertedData) {
        if (startFrom > data.length) {
            throw new IndexOutOfBoundsException("Data can be inserted only inside or at the end");
        }
        if (insertedData instanceof ByteArrayData) {
            insert(startFrom, ((ByteArrayData) insertedData).data);
        } else {
            insert(startFrom, insertedData, 0, insertedData.getDataSize());
        }
    }

    @Override
    public void insert(long startFrom, BinaryData insertedData, long insertedDataOffset, long insertedDataLength) {
        if (startFrom > data.length) {
            throw new IndexOutOfBoundsException("Data can be inserted only inside or at the end");
        }
        if (insertedData instanceof ByteArrayData) {
            insert(startFrom, ((ByteArrayData) insertedData).data);
        } else {
            long length = insertedDataLength;
            if (length > 0) {
                byte[] newData = new byte[(int) (data.length + length)];
                System.arraycopy(data, 0, newData, 0, (int) startFrom);
                for (int i = 0; i < length; i++) {
                    newData[(int) (startFrom + i)] = insertedData.getByte(insertedDataOffset + i);
                }
                System.arraycopy(data, (int) (startFrom), newData, (int) (startFrom + length), (int) (data.length - startFrom));
                data = newData;
            }
        }
    }

    @Override
    public void fillData(long startFrom, long length) {
        fillData(startFrom, length, (byte) 0);
    }

    @Override
    public void fillData(long startFrom, long length, byte fill) {
        if (length > 0) {
            Arrays.fill(data, (int) startFrom, (int) (startFrom + length), fill);
        }
    }

    @Override
    public void replace(long targetPosition, BinaryData sourceData) {
        replace(targetPosition, sourceData, 0, sourceData.getDataSize());
    }

    @Override
    public void replace(long targetPosition, BinaryData sourceData, long startFrom, long length) {
        if (targetPosition + length > getDataSize()) {
            throw new IndexOutOfBoundsException("Data can be replaced only inside or at the end");
        }

        if (sourceData instanceof ByteArrayData) {
            replace(targetPosition, ((ByteArrayData) sourceData).data, (int) startFrom, (int) length);
        } else {
            while (length > 0) {
                setByte(targetPosition, sourceData.getByte(startFrom));
                targetPosition++;
                startFrom++;
                length--;
            }
        }
    }

    @Override
    public void replace(long targetPosition, byte[] replacingData) {
        replace(targetPosition, replacingData, 0, replacingData.length);
    }

    @Override
    public void replace(long targetPosition, byte[] replacingData, int replacingDataOffset, int length) {
        if (targetPosition + length > getDataSize()) {
            throw new IndexOutOfBoundsException("Data can be replaced only inside or at the end");
        }

        try {
            System.arraycopy(replacingData, replacingDataOffset, data, (int) targetPosition, length);
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new OutOfBoundsException(ex);
        }
    }

    @Override
    public void remove(long startFrom, long length) {
        if (startFrom + length > data.length) {
            throw new IndexOutOfBoundsException("Cannot remove from " + startFrom + " with length " + length);
        }
        if (length > 0) {
            byte[] newData = new byte[(int) (data.length - length)];
            System.arraycopy(data, 0, newData, 0, (int) startFrom);
            System.arraycopy(data, (int) (startFrom + length), newData, (int) startFrom, (int) (data.length - startFrom - length));
            data = newData;
        }
    }

    @Override
    public void clear() {
        data = new byte[0];
    }

    @Override
    public void loadFromStream(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            while (inputStream.available() > 0) {
                int read = inputStream.read(buffer);
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
            data = output.toByteArray();
        }
    }

    @Override
    public long loadFromStream(InputStream inputStream, long startFrom, long maximumDataSize) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            while (inputStream.available() > 0 && maximumDataSize > 0) {
                int toRead = buffer.length;
                if (toRead > maximumDataSize) {
                    toRead = (int) maximumDataSize;
                }
                int read = inputStream.read(buffer, 0, toRead);
                if (read > 0) {
                    output.write(buffer, 0, read);
                    maximumDataSize -= read;
                }
            }
            byte[] newData = output.toByteArray();
            if (getDataSize() > startFrom + newData.length) {
                replace(startFrom, newData);
            } else {
                setDataSize(startFrom);
                insert(startFrom, newData);
            }
            return newData.length;
        }
    }

    @Override
    public OutputStream getDataOutputStream() {
        return new ByteArrayDataOutputStream(this);
    }
}
