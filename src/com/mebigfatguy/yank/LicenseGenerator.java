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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.tools.ant.Project;

public class LicenseGenerator implements Callable<Void> {

    private static final int BUFFER_SIZE = 2048;

    private Project project;
    private File destination;
    private Set<PomDetails> pomDetails;
    private Map<Pair<String, URI>, byte[]> pomLicenses;

    public LicenseGenerator(Project proj, File dest, Set<PomDetails> poms, Map<String, URI> licenses) {
        project = proj;
        pomDetails = poms;
        pomLicenses = new HashMap<>();
        for (Map.Entry<String, URI> entry : licenses.entrySet()) {
            if ((entry.getKey() != null) || (entry.getValue() != null)) {
                pomLicenses.put(new Pair<>(entry.getKey(), entry.getValue()), null);
            }
        }
        destination = new File(dest, "licenses");
        destination.mkdirs();
    }

    @Override
    public Void call() throws Exception {

        pullLicenses();
        writeLicenses();

        return null;
    }

    private void pullLicenses() {
        for (Pair<String, URI> entry : pomLicenses.keySet()) {
            BufferedInputStream bis = null;
            ByteArrayOutputStream baos = null;

            try {
                URL url = getLicenseURL(entry.getKey(), entry.getValue());
                if (url != null) {

                    bis = new BufferedInputStream(url.openStream());
                    baos = new ByteArrayOutputStream();

                    if (copy(bis, baos)) {
                        pomLicenses.put(entry, baos.toByteArray());
                    }
                }
            } catch (Exception e) {
                project.log(e.getMessage(), e, Project.MSG_VERBOSE);
                project.log("failed retrieving license file from " + entry, Project.MSG_ERR);
            } finally {
                Closer.close(bis);
                Closer.close(baos);
            }
        }
    }

    private void writeLicenses() {
        for (PomDetails pom : pomDetails) {
            Pair<String, URI> license = pom.getLicense();
            if (license != null) {
                byte[] data = pomLicenses.get(license);
                if (data != null) {
                    Artifact a = pom.getJarArtifact();
                    File licenseFile = new File(destination, a.getArtifactId() + ".license");

                    ByteArrayInputStream bais = null;
                    BufferedOutputStream bos = null;

                    try {
                        bais = new ByteArrayInputStream(data);
                        bos = new BufferedOutputStream(new FileOutputStream(licenseFile));

                        copy(bais, bos);
                    } catch (Exception e) {
                        if (licenseFile.exists()) {
                            licenseFile.deleteOnExit();
                        }
                        project.log("Failed to write license file for " + license, Project.MSG_DEBUG);
                    } finally {
                        Closer.close(bais);
                        Closer.close(bos);
                    }
                }
            }

        }
    }

    private static URL getLicenseURL(String name, URI uri) throws MalformedURLException {
        URL url = null;
        if (name != null) {
            url = LicenseGenerator.class.getResource("/knownlicenses/" + name.toLowerCase(Locale.ENGLISH));
        }

        if ((url == null) && (uri != null)) {
            url = uri.toURL();
        }

        return url;
    }

    private boolean copy(InputStream is, OutputStream os) throws InterruptedException {
        Deque<TransferBuffer> dq = new ArrayDeque<>();

        ArtifactReader r = new ArtifactReader(project, is, dq, BUFFER_SIZE, false);
        Thread rt = new Thread(r);
        rt.start();

        ArtifactWriter w = new ArtifactWriter(project, os, dq);
        Thread wt = new Thread(w);
        wt.start();

        rt.join();
        wt.join();

        return (r.wasSuccessful() && w.wasSuccessful());
    }
}
