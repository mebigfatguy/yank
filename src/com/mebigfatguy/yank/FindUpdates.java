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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.tools.ant.Project;
import org.w3c.dom.Document;

public class FindUpdates implements Runnable {

    private Project project;
    private List<Artifact> artifacts;
    File findUpdatesFile;
    List<String> repositories;

    public FindUpdates(Project proj, List<Artifact> afs, File updatesFile, List<String> servers) {
        project = proj;
        artifacts = afs;
        findUpdatesFile = updatesFile;
        repositories = servers;
    }

    @Override
    public void run() {
        PrintWriter pw = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setExpandEntityReferences(false);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            XPathFactory xpf = XPathFactory.newInstance();
            XPath xp = xpf.newXPath();
            XPathExpression xpe = xp.compile("/metadata/versioning/versions/version[last()]/text()");

            pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(findUpdatesFile), "UTF-8")));
            for (Artifact artifact : artifacts) {
                for (String server : repositories) {
                    URL u = artifact.toMetaDataURL(server);

                    InputStream is = null;
                    try {
                        is = u.openStream();
                        if (is == null) {
                            continue;
                        }
                        is = new BufferedInputStream(is);
                        Document d = db.parse(is);

                        String latest = (String) xpe.evaluate(d, XPathConstants.STRING);
                        if (newer(artifact, latest)) {
                            pw.println(artifact + " has newer version: " + latest);
                        }
                        break;

                    } finally {
                        Closer.close(is);
                    }
                }
            }
        } catch (Exception e) {
            project.log("Failed generating updated versions file " + findUpdatesFile, e, Project.MSG_ERR);
        } finally {
            Closer.close(pw);
        }
    }

    private static boolean newer(Artifact artifact, String latest) {
        if ((latest == null) || latest.isEmpty()) {
            return false;
        }

        return latest.compareTo(artifact.getVersion()) > 0;
    }

}
