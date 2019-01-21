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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.apache.tools.ant.Project;

public class Downloader implements Runnable {

    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int BUFFER_SIZE = 1024 * 16;
    private Project project;
    private Artifact artifact;
    private File destination;
    private Options options;

    public Downloader(Project p, Artifact artifact, File dest, Options options) {
        project = p;
        this.artifact = artifact;
        destination = dest;
        if (options.isSeparateClassifierTypes() && !artifact.getClassifier().isEmpty()) {
            destination = new File(destination, artifact.getClassifier());
            destination.mkdirs();
        }
        this.options = options;

    }

    @Override
    public void run() {
        File destinationFile = new File(destination, artifact.getArtifactId() + ((options.isStripVersions()) ? "" : "-" + artifact.getVersion())
                + (artifact.getClassifier().isEmpty() ? "" : ("-" + artifact.getClassifier())) + ".jar");
        for (String server : options.getServers()) {
            URL u = artifact.toURL(server);
            HttpURLConnection con = null;
            BufferedInputStream bis = null;
            BufferedOutputStream bos = null;

            try {
                if (!isUpToDate(u, destinationFile)) {
                    con = URLSupport.openURL(u, options.getProxyServer());
                    con.setConnectTimeout(CONNECTION_TIMEOUT);
                    con.connect();

                    bis = new BufferedInputStream(con.getInputStream());
                    bos = new BufferedOutputStream(new FileOutputStream(destinationFile));
                    Deque<TransferBuffer> dq = new ArrayDeque<TransferBuffer>();

                    ArtifactReader r = new ArtifactReader(project, bis, dq, BUFFER_SIZE, options.isCheckSHADigests() && !artifact.getDigest().isEmpty());
                    Thread rt = new Thread(r);
                    rt.start();

                    ArtifactWriter w = new ArtifactWriter(project, bos, dq);
                    Thread wt = new Thread(w);
                    wt.start();

                    rt.join();
                    wt.join();

                    if (r.wasSuccessful() && w.wasSuccessful()) {
                        if (options.isCheckSHADigests() && !artifact.getDigest().isEmpty()) {
                            if (!digestEquals(artifact, r.getDigest())) {
                                artifact.setStatus(Artifact.Status.DIGEST_MISMATCH);
                                destinationFile.deleteOnExit();
                                project.log("download failed with incorrect digest: " + artifact + " - actual: " + byteArrayDigestToHexString(r.getDigest()),
                                        Project.MSG_ERR);
                                return;
                            }
                        }
                        artifact.setStatus(Artifact.Status.DOWNLOADED);
                    }
                } else {
                    artifact.setStatus(Artifact.Status.UPTODATE);
                }

                project.log("download successful: " + artifact, (artifact.getStatus() != Artifact.Status.UPTODATE) ? Project.MSG_ERR : Project.MSG_VERBOSE);
                return;
            } catch (Exception e) {
                if (!YankTask.SOURCE_CLASSIFIER.equals(artifact.getClassifier())) {
                    project.log(e.getMessage(), e, Project.MSG_VERBOSE);
                    project.log("download failed: " + artifact, Project.MSG_ERR);
                }
                artifact.setStatus(Artifact.Status.FAILED);
            } finally {
                Closer.close(bis);
                Closer.close(bos);
                Closer.close(con);
            }
        }
    }

    private boolean isUpToDate(URL u, File dest) throws IOException {
        if (!dest.isFile()) {
            return false;
        }

        HttpURLConnection con = null;

        try {
            con = URLSupport.openURL(u, options.getProxyServer());
            con.setConnectTimeout(CONNECTION_TIMEOUT);
            con.setRequestMethod("HEAD");
            con.connect();

            String serverLen = con.getHeaderField("Content-Length");
            if (serverLen != null) {
                long contentLength = Long.parseLong(serverLen);
                if (dest.length() != contentLength) {
                    return false;
                }
            }

            return true;
        } finally {
            Closer.close(con);
        }
    }

    private boolean digestEquals(Artifact artf, byte[] computedDigest) {
        byte[] expectedDigest = hexStringDigestToByteArray(artf.getDigest());
        return (Arrays.equals(expectedDigest, computedDigest));
    }

    private static byte[] hexStringDigestToByteArray(String hexDigest) {
        int len = hexDigest.length();
        byte[] digest = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            digest[i / 2] = (byte) ((Character.digit(hexDigest.charAt(i), 16) << 4) + Character.digit(hexDigest.charAt(i + 1), 16));
        }
        return digest;
    }

    final private static char[] hexChars = "0123456789ABCDEF".toCharArray();

    public static String byteArrayDigestToHexString(byte[] digest) {
        char[] hexDigest = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int b = digest[i] & 0xFF;
            hexDigest[i << 1] = hexChars[b >>> 4];
            hexDigest[i << (1 + 1)] = hexChars[b & 0x0F];
        }
        return new String(hexDigest);
    }
}
