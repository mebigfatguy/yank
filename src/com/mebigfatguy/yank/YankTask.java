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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

public class YankTask extends Task {

    private File xlsFile;
    private File destination;
    private boolean reportMissingDependencies = false;
    private List<String> servers = new ArrayList<String>();

    public void setYankFile(File xls) {
        xlsFile = xls;
    }

    public void setDestination(File dest) {
        destination = dest;
    }

    public void setReportMissingDependencies(boolean report) {
        reportMissingDependencies = report;
    }

    public void addConfiguredServer(String server) {
        servers.add(server);
    }

    public void execute() throws BuildException {
        if (!xlsFile.isFile())
            throw new BuildException("Yank (xls) file not specified or invalid: " + xlsFile);
        if (destination.isFile())
            throw new BuildException("Yank destination (" + destination + ") is a file, not a directory");
        if (servers.isEmpty())
            throw new BuildException("No specified nested <server> items found");


        destination.mkdirs();

        List<Artifact> artifacts = readArtifactList();
    }

    private List<Artifact> readArtifactList() {
        List<Artifact> artifacts = new ArrayList<Artifact>();

        return artifacts;
    }
}
