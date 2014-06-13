/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2014 MeBigFatGuy.com
 * Copyright 2013-2014 Dave Brosius
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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.tools.ant.Project;

public class LicenseGenerator implements Callable<Void> {

    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int BUFFER_SIZE = 2048;
    
	private Project project;
	private Options options;
	private File destination;
	private Set<PomDetails> pomDetails;
	private Map<URI, byte[]> pomLicenses;
	
	public LicenseGenerator(Project proj, Options opts, File dest, Set<PomDetails> poms, Map<String, URI> licenses) throws URISyntaxException {
		project = proj;
		options = opts;
		pomDetails = poms;
		pomLicenses = new HashMap<URI, byte[]>();
		for (URI u : licenses.values()) {
			pomLicenses.put(u,  null);
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
		for (URI u : pomLicenses.keySet()) {
			HttpURLConnection con = null;
            BufferedInputStream bis = null;
            ByteArrayOutputStream baos = null;

            try {
                con = URLSupport.openURL(u.toURL(), options.getProxyServer());
                con.setConnectTimeout(CONNECTION_TIMEOUT);
                con.connect();

                bis = new BufferedInputStream(con.getInputStream());
                baos = new ByteArrayOutputStream();
                
                if (copy(bis, baos)) {
                    pomLicenses.put(u,  baos.toByteArray());
                }
            } catch (Exception e) {
            	project.log(e.getMessage(), e, Project.MSG_VERBOSE);
            	project.log("failed retrieving license file from " + u, Project.MSG_ERR);
            } finally {
                Closer.close(bis);
                Closer.close(baos);
                Closer.close(con);
            }
		}
	}
	
	private void writeLicenses() {
		for (PomDetails pom : pomDetails) {
			Pair<String, URI> license = pom.getLicense();
			URI u = license.getValue();
			if (u != null) {
				byte[] data = pomLicenses.get(u);
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
		            	project.log("Failed to write license file for " + u, Project.MSG_DEBUG);
		            } finally {
		            	Closer.close(bais);
		            	Closer.close(bos);
		            }
				}
			}
					
		}
	}
	
	private boolean copy(InputStream is, OutputStream os) throws InterruptedException {
        Deque<TransferBuffer> dq = new ArrayDeque<TransferBuffer>();

        ArtifactReader r = new ArtifactReader(project, is, dq, BUFFER_SIZE);
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
