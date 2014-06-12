/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2014MeBigFatGuy.com
 * Copyright 2013-2014Dave Brosius
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.tools.ant.Project;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public class PomDiscovery implements Callable<PomDetails> {

    private static final int CONNECTION_TIMEOUT = 10000;

    private Project project;
    private Artifact artifact;
    private Options options;


    public PomDiscovery(Project p, Artifact artifact, Options options) {
        project = p;
        this.artifact = artifact;
        this.options = options;
    }

    @Override
    public PomDetails call() throws Exception {
        project.log("building pom details for: " + artifact, Project.MSG_VERBOSE);
        List<Artifact> transitiveArtifacts = new ArrayList<Artifact>();
        XMLReader reader = XMLReaderFactory.createXMLReader();
        PomHandler handler = new PomHandler(transitiveArtifacts);

        for (String server : options.getServers()) {
            URL u = artifact.pomURL(server);
            HttpURLConnection con = null;
            BufferedInputStream bis = null;

            try {
                con = URLSupport.openURL(u, options.getProxyServer());
                con.setConnectTimeout(CONNECTION_TIMEOUT);
                con.connect();

                bis = new BufferedInputStream(con.getInputStream());

                reader.setContentHandler(handler);

                try {
                    reader.parse(new InputSource(bis));
                } catch (PomParseCompletedException ppce) {
                }

                break;
            } catch (Exception e) {
                project.log(e.getMessage(), Project.MSG_VERBOSE);
            } finally {
                Closer.close(bis);
                Closer.close(con);
            }
        }

        return new PomDetails(artifact, transitiveArtifacts, handler.getLicenseURL());
    }
}
