/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2015 MeBigFatGuy.com
 * Copyright 2013-2015 Dave Brosius
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations
 * under the License.
 */
package com.mebigfatguy.yank;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

class LengthLimitingInputStream extends FilterInputStream {
    private long length;

    public LengthLimitingInputStream(InputStream originalStream, long len) {
        super(originalStream);
        length = len;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public int read(byte[] buffer, int offset, int requestLen) throws IOException {
        if (length <= 0) {
            return -1;
        }

        if (requestLen > length) {
            requestLen = (int) length;
        }

        int bytes = super.read(buffer, offset, requestLen);
        length -= bytes;
        return bytes;
    }

    @Override
    public int read() throws IOException {
        if (length <= 0) {
            return -1;
        }

        int value = super.read();
        length--;
        return value;
    }

    @Override
    public long skip(long skipLen) throws IOException {

        if (skipLen > length) {
            skipLen = length;
        }

        long bytes = super.skip(skipLen);
        length -= bytes;
        return bytes;
    }

    @Override
    public int available() throws IOException {
        int bytes = super.available();
        if (bytes > length) {
            bytes = (int) length;
        }

        return bytes;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void mark(int readlimit) {
        throw new UnsupportedOperationException("mark not supported on LengthLimitingInputStream");
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("reset not supported or LengthLimitingInputStream");
    }

}