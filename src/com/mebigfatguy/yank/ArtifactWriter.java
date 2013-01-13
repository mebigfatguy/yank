/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013 MeBigFatGuy.com
 * Copyright 2013 Dave Brosius
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

import java.io.OutputStream;
import java.util.Deque;

import org.apache.tools.ant.Project;

class ArtifactWriter implements Runnable {

    private final Project project;
    private final OutputStream outputStream;
    private final Deque<TransferBuffer> dq;
    private boolean success = false;

    public ArtifactWriter(Project p, final OutputStream os, final Deque<TransferBuffer> queue) {
        project = p;
        outputStream = os;
        dq = queue;
    }

    @Override
    public void run() {
        try {
            TransferBuffer buffer = null;
            int size = 0;

            while (size >= 0) {
                synchronized (dq) {
                    while (dq.size() == 0) {
                        dq.wait();
                    }
                    buffer = dq.removeFirst();
                }

                size = buffer.getSize();
                if (size > 0) {
                    outputStream.write(buffer.getBuffer(), 0, size);
                }
            }
            success = true;
        } catch (Exception e) {
            project.log(e.getMessage(), Project.MSG_ERR);
        }
    }

    public boolean wasSuccessful() {
        return success;
    }
}