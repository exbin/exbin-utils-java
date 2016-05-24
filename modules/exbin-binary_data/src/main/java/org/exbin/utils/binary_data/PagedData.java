/*
 * Copyright (C) ExBin Project
 *
 * This application or library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This application or library is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along this application.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.exbin.utils.binary_data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encapsulation class for binary data blob.
 *
 * Data are stored using paging. Last page might be shorter than page size, but
 * not empty.
 *
 * @version 0.1.0 2016/05/23
 * @author ExBin Project (http://exbin.org)
 */
public class PagedData implements EditableBinaryData {

    public int DEFAULT_PAGE_SIZE = 4096;

    private int pageSize = DEFAULT_PAGE_SIZE;
    private final List<byte[]> data = new ArrayList<>();

    public PagedData() {
    }

    public PagedData(int pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public long getDataSize() {
        return (data.size() > 1 ? (data.size() - 1) * pageSize : 0) + (data.size() > 0 ? data.get(data.size() - 1).length : 0);
    }

    @Override
    public void setDataSize(long size) {
        long dataSize = getDataSize();
        if (size > dataSize) {
            int lastPage = (int) (dataSize / pageSize);
            int lastPageSize = (int) (dataSize % pageSize);
            long remaining = size - dataSize;
            // extend last page
            if (lastPageSize > 0) {
                byte[] page = getPage(lastPage);
                int nextPageSize = remaining + lastPageSize > pageSize ? pageSize : (int) remaining + lastPageSize;
                byte[] newPage = new byte[nextPageSize];
                System.arraycopy(page, 0, newPage, 0, lastPageSize);
                setPage(lastPage, newPage);
                remaining -= (nextPageSize - lastPageSize);
                lastPage++;
            }

            while (remaining > 0) {
                int nextPageSize = remaining > pageSize ? pageSize : (int) remaining;
                data.add(new byte[nextPageSize]);
                remaining -= nextPageSize;
            }
        } else if (size < dataSize) {
            int lastPage = (int) (size / pageSize);
            int lastPageSize = (int) (size % pageSize);
            // shrink last page
            if (lastPageSize > 0) {
                byte[] page = getPage(lastPage);
                byte[] newPage = new byte[lastPageSize];
                System.arraycopy(page, 0, newPage, 0, lastPageSize);
                setPage(lastPage, newPage);
                lastPage++;
            }

            for (int pageIndex = data.size() - 1; pageIndex >= lastPage; pageIndex--) {
                data.remove(pageIndex);
            }
        }
    }

    @Override
    public byte getByte(long position) {
        byte[] page = getPage((int) (position / pageSize));
        return page[(int) (position % pageSize)];
    }

    @Override
    public void setByte(long position, byte value) {
        byte[] page;
        page = getPage((int) (position / pageSize));
        page[(int) (position % pageSize)] = value;
    }

    @Override
    public void insertUninitialized(long startFrom, long length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length of inserted block must be nonnegative");
        }
        if (startFrom < 0) {
            throw new IllegalArgumentException("Position of inserted block must be nonnegative");
        }
        long dataSize = getDataSize();
        if (startFrom > dataSize) {
            throw new IllegalArgumentException("Inserted block must be inside or directly after existing data");
        }

        if (startFrom >= dataSize) {
            setDataSize(startFrom + length);
        } else if (length > 0) {
            long copyLength = dataSize - startFrom;
            dataSize = dataSize + length;
            setDataSize(dataSize);
            long sourceEnd = dataSize - length;
            long targetEnd = dataSize;
            // Backward copy
            while (copyLength > 0) {
                byte[] sourcePage = getPage((int) (sourceEnd / pageSize));
                int sourceOffset = (int) (sourceEnd % pageSize);
                if (sourceOffset == 0) {
                    sourcePage = getPage((int) ((sourceEnd - 1) / pageSize));
                    sourceOffset = sourcePage.length;
                }

                byte[] targetPage = getPage((int) (targetEnd / pageSize));
                int targetOffset = (int) (targetEnd % pageSize);
                if (targetOffset == 0) {
                    targetPage = getPage((int) ((targetEnd - 1) / pageSize));
                    targetOffset = targetPage.length;
                }

                int copySize = sourceOffset > targetOffset ? targetOffset : sourceOffset;
                if (copySize > copyLength) {
                    copySize = (int) copyLength;
                }

                System.arraycopy(sourcePage, sourceOffset - copySize, targetPage, targetOffset - copySize, copySize);
                copyLength -= copySize;
                sourceEnd -= copySize;
                targetEnd -= copySize;
            }
        }
    }

    @Override
    public void insert(long startFrom, long length) {
        insertUninitialized(startFrom, length);
        fillData(startFrom, length);
    }

    @Override
    public void insert(long startFrom, BinaryData insertedData) {
        long dataSize = insertedData.getDataSize();
        insertUninitialized(startFrom, dataSize);
        replace(startFrom, insertedData, 0, dataSize);
    }

    @Override
    public void insert(long startFrom, BinaryData insertedData, long insertedDataOffset, long insertedDataLength) {
        long dataSize = insertedData.getDataSize();
        insertUninitialized(startFrom, dataSize);
        replace(startFrom, insertedData, insertedDataOffset, insertedDataLength);
    }

    @Override
    public void insert(long startFrom, byte[] insertedData) {
        insert(startFrom, insertedData, 0, insertedData.length);
    }

    @Override
    public void insert(long startFrom, byte[] insertedData, int insertedDataOffset, int insertedDataLength) {
        if (insertedDataLength <= 0) {
            return;
        }

        insertUninitialized(startFrom, insertedDataLength);

        while (insertedDataLength > 0) {
            byte[] targetPage = getPage((int) (startFrom / pageSize));
            int targetOffset = (int) (startFrom % pageSize);
            int blockLength = pageSize - targetOffset;
            if (blockLength > insertedDataLength) {
                blockLength = insertedDataLength;
            }

            System.arraycopy(insertedData, insertedDataOffset, targetPage, targetOffset, blockLength);
            insertedDataOffset += blockLength;
            insertedDataLength -= blockLength;
            startFrom += blockLength;
        }
    }

    @Override
    public void fillData(long startFrom, long length) {
        fillData(startFrom, length, (byte) 0);
    }

    @Override
    public void fillData(long startFrom, long length, byte fill) {
        if (length < 0) {
            throw new IllegalArgumentException("Length of filled block must be nonnegative");
        }
        if (startFrom < 0) {
            throw new IllegalArgumentException("Position of filler block must be nonnegative");
        }
        if (startFrom + length > getDataSize()) {
            throw new IllegalArgumentException("Filled block must be inside existing data");
        }

        while (length > 0) {
            byte[] page = getPage((int) (startFrom / pageSize));
            int pageOffset = (int) (startFrom % pageSize);
            int fillSize = page.length - pageOffset;
            if (fillSize > length) {
                fillSize = (int) length;
            }
            Arrays.fill(page, pageOffset, pageOffset + fillSize, (byte) fill);
            length -= fillSize;
            startFrom += fillSize;
        }
    }

    @Override
    public PagedData copy() {
        PagedData targetData = new PagedData();
        targetData.replace(0, this);
        return targetData;
    }

    @Override
    public PagedData copy(long startFrom, long length) {
        PagedData targetData = new PagedData();
        targetData.insertUninitialized(0, length);
        targetData.replace(0, this, startFrom, length);
        return targetData;
    }

    @Override
    public void copyToArray(long startFrom, byte[] target, int offset, int length) {
        while (length > 0) {
            byte[] page = getPage((int) (startFrom / pageSize));
            int pageOffset = (int) (startFrom % pageSize);
            int copySize = pageSize - pageOffset;
            if (copySize > length) {
                copySize = length;
            }

            try {
                System.arraycopy(page, pageOffset, target, offset, copySize);
            } catch (ArrayIndexOutOfBoundsException ex) {
                throw new OutOfBoundsException(ex);
            }
            length -= copySize;
            offset += copySize;
            startFrom += copySize;
        }
    }

    @Override
    public void remove(long startFrom, long length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length of removed block must be nonnegative");
        }
        if (startFrom < 0) {
            throw new IllegalArgumentException("Position of removed block must be nonnegative");
        }
        if (startFrom + length > getDataSize()) {
            throw new IllegalArgumentException("Removed block must be inside existing data");
        }

        if (length > 0) {
            replace(startFrom, this, startFrom + length, getDataSize() - startFrom - length);
            setDataSize(getDataSize() - length);
        }
    }

    @Override
    public void clear() {
        data.clear();
    }

    public int getPagesCount() {
        return data.size();
    }

    public int getPageSize() {
        return pageSize;
    }

    /**
     * Gets data page allowing direct access to it.
     *
     * @param pageIndex page index
     * @return data page
     */
    public byte[] getPage(int pageIndex) {
        return data.get(pageIndex);
    }

    /**
     * Sets data page replacing existing page by reference.
     *
     * @param pageIndex page index
     * @param dataPage data page
     */
    public void setPage(int pageIndex, byte[] dataPage) {
        data.set(pageIndex, dataPage);
    }

    @Override
    public void replace(long targetPosition, BinaryData sourceData) {
        replace(targetPosition, sourceData, 0, sourceData.getDataSize());
    }

    @Override
    public void replace(long targetPosition, BinaryData sourceData, long startFrom, long length) {
        if (targetPosition + length > getDataSize()) {
            setDataSize(targetPosition + length);
        }

        if (sourceData instanceof PagedData) {
            if (sourceData != this || (startFrom > targetPosition) || (startFrom + length < targetPosition)) {
                while (length > 0) {
                    byte[] page = getPage((int) (targetPosition / pageSize));
                    int offset = (int) (targetPosition % pageSize);

                    byte[] sourcePage = ((PagedData) sourceData).getPage((int) (startFrom / ((PagedData) sourceData).getPageSize()));
                    int sourceOffset = (int) (startFrom % ((PagedData) sourceData).getPageSize());

                    int copySize = pageSize - offset;
                    if (copySize > ((PagedData) sourceData).getPageSize() - sourceOffset) {
                        copySize = (int) (((PagedData) sourceData).getPageSize() - sourceOffset);
                    }
                    if (copySize > length) {
                        copySize = (int) length;
                    }

                    try {
                        System.arraycopy(sourcePage, sourceOffset, page, offset, copySize);
                    } catch (ArrayIndexOutOfBoundsException ex) {
                        throw new OutOfBoundsException(ex);
                    }
                    length -= copySize;
                    targetPosition += copySize;
                    startFrom += copySize;
                }
            } else {
                targetPosition += length - 1;
                startFrom += length - 1;
                while (length > 0) {
                    byte[] page = getPage((int) (targetPosition / pageSize));
                    int upTo = (int) (targetPosition % pageSize) + 1;

                    byte[] sourcePage = ((PagedData) sourceData).getPage((int) (startFrom / ((PagedData) sourceData).getPageSize()));
                    int sourceUpTo = (int) (startFrom % ((PagedData) sourceData).getPageSize()) + 1;

                    int copySize = upTo;
                    if (copySize > sourceUpTo) {
                        copySize = sourceUpTo;
                    }
                    if (copySize > length) {
                        copySize = (int) length;
                    }
                    int offset = upTo - copySize;
                    int sourceOffset = sourceUpTo - copySize;

                    System.arraycopy(sourcePage, sourceOffset, page, offset, copySize);
                    length -= copySize;
                    targetPosition -= copySize;
                    startFrom -= copySize;
                }
            }
        } else {
            while (length > 0) {
                byte[] page = getPage((int) (targetPosition / pageSize));
                int offset = (int) (targetPosition % pageSize);

                int copySize = pageSize - offset;
                if (copySize > length) {
                    copySize = (int) length;
                }

                sourceData.copyToArray(startFrom, page, offset, copySize);

                length -= copySize;
                targetPosition += copySize;
                startFrom += copySize;
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
            setDataSize(targetPosition + length);
        }

        while (length > 0) {
            byte[] page = getPage((int) (targetPosition / pageSize));
            int offset = (int) (targetPosition % pageSize);

            int copySize = pageSize - offset;
            if (copySize > length) {
                copySize = (int) length;
            }

            System.arraycopy(replacingData, replacingDataOffset, page, offset, copySize);

            length -= copySize;
            targetPosition += copySize;
            replacingDataOffset += copySize;
        }
    }

    @Override
    public void loadFromStream(InputStream inputStream) {
        try {
            data.clear();
            byte[] buffer = new byte[pageSize];
            int cnt;
            int offset = 0;
            while ((cnt = inputStream.read(buffer, offset, buffer.length - offset)) > 0) {
                if (cnt + offset < pageSize) {
                    offset = offset + cnt;
                } else {
                    data.add(buffer);
                    buffer = new byte[pageSize];
                    offset = 0;
                }
            }

            if (offset > 0) {
                byte[] tail = new byte[offset];
                System.arraycopy(buffer, 0, tail, 0, offset);
                data.add(tail);
            }
        } catch (IOException ex) {
            Logger.getLogger(PagedData.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public long loadFromStream(InputStream inputStream, long startFrom, long dataSize) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
        /*try {
            long loadedData = 0;
            data.clear();
            while (dataSize > 0) {
                int blockSize = dataSize < pageSize ? (int) dataSize : pageSize;
                byte[] page = new byte[blockSize];

                int offset = 0;
                while (blockSize > 0) {
                    int red = inputStream.read(page, offset, blockSize);
                    if (red == -1) {
                        throw new IOException("Unexpected data processed - " + dataSize + " expected, but not met.");
                    } else {
                        offset += red;
                        blockSize -= red;
                    }
                }

                data.add(page);
                dataSize -= page.length;
            }
        } catch (IOException ex) {
            Logger.getLogger(PagedData.class.getName()).log(Level.SEVERE, null, ex);
        } */
    }

    @Override
    public void saveToStream(OutputStream outputStream) throws IOException {
        for (byte[] dataPage : data) {
            outputStream.write(dataPage);
        }
    }
}
