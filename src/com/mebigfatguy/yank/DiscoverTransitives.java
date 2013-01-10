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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.tools.ant.Project;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public class DiscoverTransitives implements Callable<List<Artifact>> {

    private static final int CONNECTION_TIMEOUT = 10000;

    private Project project;
    private Artifact artifact;
    private Options options;

    public DiscoverTransitives(Project p, Artifact artifact, Options options) {
        project = p;
        this.artifact = artifact;
        this.options = options;
    }

    @Override
    public List<Artifact> call() throws Exception {
        List<Artifact> transitiveArtifacts = new ArrayList<Artifact>();
        XMLReader reader = XMLReaderFactory.createXMLReader();

        for (String server : options.getServers()) {
            URL u = artifact.pomURL(server);
            HttpURLConnection con = null;
            BufferedInputStream bis = null;

            try {
                con = URLSupport.openURL(u, options.getProxyServer());
                con.setConnectTimeout(CONNECTION_TIMEOUT);
                con.connect();

                bis = new BufferedInputStream(con.getInputStream());

                reader.setContentHandler(new PomHandler(transitiveArtifacts));

                try {
                    reader.parse(new InputSource(bis));
                } catch (PomParseCompletedException ppce) {
                }

                break;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Closer.close(bis);
                Closer.close(con);
            }
        }

        return transitiveArtifacts;
    }
}
