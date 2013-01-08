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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.apache.tools.ant.Project;

public class Downloader implements Runnable {

    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int BUFFER_SIZE = 1024 * 16;
    private Project project;
    private Artifact artifact;
    private List<String> servers;
    private File destination;

    public Downloader(Project p, Artifact artifact, List<String> servers, File dest) {
        project = p;
        this.artifact = artifact;
        this.servers = servers;
        destination = dest;
    }

    @Override
    public void run() {

        File destinationFile = new File(destination, artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar");
        for (String server : servers) {
            URL u = artifact.toURL(server);
            HttpURLConnection con = null;
            BufferedInputStream bis = null;
            BufferedOutputStream bos = null;

            try {
                if (!isUpToDate(u, destinationFile)) {
                    con = (HttpURLConnection) u.openConnection();
                    con.setConnectTimeout(CONNECTION_TIMEOUT);
                    con.connect();

                    bis = new BufferedInputStream(con.getInputStream());
                    bos = new BufferedOutputStream(new FileOutputStream(destinationFile));
                    Deque<TransferBuffer> dq = new ArrayDeque<TransferBuffer>();

                    ArtifactReader r = new ArtifactReader(project, bis, dq, BUFFER_SIZE);
                    Thread rt = new Thread(r);
                    rt.start();

                    ArtifactWriter w = new ArtifactWriter(project, bos, dq);
                    Thread wt = new Thread(w);
                    wt.start();

                    rt.join();
                    wt.join();

                    if (r.wasSuccessful() && w.wasSuccessful()) {
                        artifact.setStatus(Artifact.Status.DOWNLOADED);
                    }
                }

                project.log("download successful: " + artifact);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Closer.close(bis);
                Closer.close(bos);
                Closer.close(con);
            }

            project.log("download failed: " + artifact);
            artifact.setStatus(Artifact.Status.FAILED);
        }
    }

    private boolean isUpToDate(URL u, File destination) throws IOException {
        if (!destination.isFile()) {
            return false;
        }

        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) u.openConnection();
            con.setConnectTimeout(CONNECTION_TIMEOUT);
            con.setRequestMethod("HEAD");
            con.connect();

            String serverLen = con.getHeaderField("Content-Length");
            if (serverLen != null) {
                long contentLength = Long.parseLong(serverLen);
                if (destination.length() != contentLength) {
                    return false;
                }
            }

            return true;
        } finally {
            Closer.close(con);
        }
    }
}
