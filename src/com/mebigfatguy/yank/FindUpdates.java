package com.mebigfatguy.yank;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.List;

import org.apache.tools.ant.Project;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

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
            DocumentBuilder db = dbf.newDocumentBuilder();
            XPathFactory xpf = XPathFactory.newInstance();
            XPath xp = xpf.newXPath();
            XPathExpression xpe = xp.compile("/metadata/versioning/versions/version[last()]/text()");
            
            pw = new PrintWriter(new BufferedWriter(new FileWriter(findUpdatesFile)));
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
    
    private boolean newer(Artifact artifact, String latest) {
        if ((latest == null) || latest.isEmpty()) {
            return false;
        }
        
        return latest.compareTo(artifact.getVersion()) > 0;
    }

}
