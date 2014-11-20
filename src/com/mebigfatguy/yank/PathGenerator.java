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

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

public class PathGenerator implements Runnable {
    
    private Project project;
    private List<Artifact> artifacts;
    private GeneratePathTask generatePathTask;
    private boolean stripVersions;
    
    public PathGenerator(Project proj, List<Artifact> afs, GeneratePathTask gpTask, boolean stripVers) {
        project = proj;
        artifacts = afs;
        generatePathTask = gpTask;
        stripVersions = stripVers;
    }
    
    @Override
    public void run() {
        if (generatePathTask.getPathXmlFile() != null)
            generateProjectFile();
        
        String libDir = project.replaceProperties(generatePathTask.getLibraryDirName());
        
        Path path = new Path(project);
        for (Artifact artifact : artifacts) {
            PathElement element = path.createPathElement();

            StringBuilder jarPath = new StringBuilder(libDir).append("/").append(artifact.getArtifactId());
            if (!stripVersions) {
                jarPath.append('-');
                jarPath.append(artifact.getVersion());
            }
            jarPath.append(".jar");
            element.setPath(jarPath.toString());
        }
        
        project.log("Setting property " + generatePathTask.getClasspathName() + " to " + path, Project.MSG_VERBOSE);
        project.addReference(project.replaceProperties(generatePathTask.getClasspathName()), path);
    }
    
    private void generateProjectFile() {
        PrintWriter pw = null;
        try {  
            pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(generatePathTask.getPathXmlFile()), "UTF-8")));
            Collections.sort(artifacts);
    
            pw.println("<project name=\"yank\">");
            pw.print("\t<path id=\"");
            pw.print(generatePathTask.getClasspathName());
            pw.println("\">");
    
            String dirName = generatePathTask.getLibraryDirName();
            boolean needPathSlash = !"/\\".contains(dirName.substring(dirName.length() - 1));
    
            for (Artifact artifact : artifacts) {
                pw.print("\t\t<pathelement location=\"");
                pw.print(generatePathTask.getLibraryDirName());
                if (needPathSlash)
                    pw.print("/");
                pw.print(artifact.getArtifactId());
                if (!stripVersions) {
                    pw.print('-');
                    pw.print(artifact.getVersion());
                }
                pw.println(".jar\" />");
            }
            pw.println("\t</path>");
            pw.println("</project>");
        } catch (Exception e) {
            project.log("Failed generating classpath " + generatePathTask.getClasspathName() + " in file " + generatePathTask.getPathXmlFile(), e, Project.MSG_ERR);
        } finally {
            Closer.close(pw);
        }
    }
}
