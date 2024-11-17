/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2019 MeBigFatGuy.com
 * Copyright 2013-2019 Dave Brosius
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

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Deque;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

class ArtifactReader implements Runnable {
    private final Project project;
    private final InputStream inputStream;
    private final Deque<TransferBuffer> dq;
    private final int bufferSize;
    private MessageDigest messageDigest;
    private byte[] actualDigest;
    private boolean success = false;

    public ArtifactReader(Project p, final InputStream is, final Deque<TransferBuffer> queue, int bufferSize, boolean checkSHADigest) {
        project = p;
        inputStream = is;
        dq = queue;
        this.bufferSize = bufferSize;
        if (checkSHADigest) {
            try {
                messageDigest = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new BuildException("Can't create SHA-1 digest of artifact", e);
            }
        }
    }

    @Override
    public void run() {
        try {
            byte[] buffer = new byte[bufferSize];
            int size = inputStream.read(buffer);
            while (size >= 0) {
                if (size > 0) {
                    if (messageDigest != null) {
                        messageDigest.update(buffer, 0, size);
                    }

                    TransferBuffer queueBuffer = new TransferBuffer(buffer, size);
                    synchronized (dq) {
                        dq.addLast(queueBuffer);
                        dq.notifyAll();
                    }
                    buffer = new byte[bufferSize];
                    size = inputStream.read(buffer);
                }
            }
            success = true;
        } catch (Exception e) {
            project.log(e.getMessage(), Project.MSG_ERR);
        } finally {
            TransferBuffer queueBuffer = new TransferBuffer(null, -1);
            synchronized (dq) {
                dq.addLast(queueBuffer);
                dq.notifyAll();
            }
        }
    }

    public byte[] getDigest() {
    	if (actualDigest != null) {
    		return actualDigest;
    	}
    	
        if (messageDigest == null) {
            return null;
        }
        actualDigest = messageDigest.digest();
        return actualDigest;
    }

    public boolean wasSuccessful() {
        return success;
    }
}