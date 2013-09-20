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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

import org.apache.tools.ant.Project;

public class VersionsGenerator implements Runnable {

    private Project project;
    private List<Artifact> artifacts;
    GenerateVersionsTask generateVersionsTask;
    
    public VersionsGenerator(Project proj, List<Artifact> afs, GenerateVersionsTask gvTask) {
        project = proj;
        artifacts = afs;
        generateVersionsTask = gvTask;
    }
    
    public void run() {
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new BufferedWriter(new FileWriter(generateVersionsTask.getPropertyFileName())));
            for (Artifact artifact : artifacts) {
                if (artifact.getAlternate().isEmpty()) {
                    pw.println(artifact.getArtifactId() + ".version = " + artifact.getVersion());
                    project.setProperty(artifact.getArtifactId() + ".version", artifact.getVersion());
                }
            }
        } catch (Exception e) {
            project.log("Failed generating versions property file " + generateVersionsTask.getPropertyFileName(), e, Project.MSG_ERR);
        } finally {
            Closer.close(pw);
        }
    }
}
