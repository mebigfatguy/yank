/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2018 MeBigFatGuy.com
 * Copyright 2013-2018 Dave Brosius
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
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

public class YankTask extends Task {

    static final String SOURCE_CLASSIFIER = "sources";
    static final String JAR = "jar";

    private File xlsFile;
    private File destination;
    private boolean reportMissingDependencies;
    private File findUpdatesFile;
    private GeneratePathTask generatePathTask;
    private GenerateVersionsTask generateVersionsTask;
    private boolean generateLicenses;
    private boolean failOnError = true;
    private int threadPoolSize = 4 * Runtime.getRuntime().availableProcessors();
    private Options options = new Options();

    public void setYankFile(File xls) {
        xlsFile = xls;
    }

    public void setDestination(File dest) {
        destination = dest;
    }

    public void setFailOnError(boolean fail) {
        failOnError = fail;
    }

    public void setReportMissingDependencies(boolean report) {
        reportMissingDependencies = report;
    }

    public void setFindUpdatesFile(File updatesFile) {
        findUpdatesFile = updatesFile;
    }

    public void setStripVersions(boolean strip) {
        options.setStripVersions(strip);
    }

    public void setSource(boolean sources) {
        options.setYankSources(sources);
    }

    public void setSeparateClassifierTypes(boolean separate) {
        options.setSeparateClassifierTypes(separate);
    }

    public void setGenerateLicenses(boolean generate) {
        generateLicenses = generate;
    }

    public void setThreadPoolSize(int size) {
        threadPoolSize = size;
    }

    public void addConfiguredGeneratePath(GeneratePathTask gpTask) {
        generatePathTask = gpTask;
    }

    public void addConfiguredServer(ServerTask server) {
        String url = server.getUrl().trim();
        if (!url.endsWith("/")) {
            url += "/";
        }
        options.addServer(url);
    }

    public void addConfiguredGenerateVersions(GenerateVersionsTask gvTask) {
        generateVersionsTask = gvTask;
    }

    public void setProxyServer(String proxy) {
        proxy = proxy.trim();
        if (proxy.isEmpty()) {
            getProject().log("Empty proxy server specified, ignored");
        } else {
            options.setProxyServer(proxy);
        }
    }

    public void setCheckSHADigests(boolean check) {
        options.setCheckSHADigests(check);
    }

    @Override
    public void execute() throws BuildException {
        getProject().log("Checking attributes...", Project.MSG_VERBOSE);
        if ((xlsFile == null) || !xlsFile.isFile()) {
            throw new BuildException("Yank (*.xls, *.xlsx, *.ods, *.json) file not specified or invalid: " + xlsFile);
        }
        if ((destination == null) || destination.isFile()) {
            throw new BuildException("Yank destination (" + destination + ") is a file, not a directory");
        }
        if (options.getServers().isEmpty()) {
            throw new BuildException("No specified nested <server> items found");
        }

        if (!destination.mkdirs() && !destination.exists()) {
            throw new BuildException("Failed creating destination directory: " + destination);
        }

        ExecutorService pool = Executors.newFixedThreadPool(threadPoolSize);
        try {

            getProject().log("Reading artifact list...", Project.MSG_VERBOSE);
            final List<Artifact> artifacts = SpreadsheetParserFactory.parse(getProject(), xlsFile);
            List<Future<?>> downloadFutures = new ArrayList<Future<?>>(artifacts.size());

            getProject().log("Scheduling downloaders...", Project.MSG_VERBOSE);
            for (Artifact artifact : artifacts) {
                downloadFutures.add(pool.submit(new Downloader(getProject(), artifact, destination, options)));
                if (options.isYankSources() && (artifact.getClassifier().isEmpty())) {
                    Artifact sourceArtifact = new Artifact(artifact.getGroupId(), artifact.getArtifactId(), JAR, SOURCE_CLASSIFIER, artifact.getVersion(), "");
                    downloadFutures.add(pool.submit(new Downloader(getProject(), sourceArtifact, destination, options)));
                }
            }

            List<Future<PomDetails>> pomFutures = null;
            if (reportMissingDependencies || generateLicenses) {
                if (reportMissingDependencies) {
                    getProject().log("Scheduling missing dependencies check...", Project.MSG_VERBOSE);
                }
                if (generateLicenses) {
                    getProject().log("Scheduling license generation...", Project.MSG_VERBOSE);
                }
                pomFutures = new ArrayList<Future<PomDetails>>(artifacts.size());
                for (Artifact artifact : artifacts) {
                    if (artifact.getClassifier().length() == 0) {
                        pomFutures.add(pool.submit(new PomDiscovery(getProject(), artifact, options)));
                    }
                }
            }

            if (generatePathTask != null) {
                getProject().log("Scheduling path generation task...", Project.MSG_VERBOSE);
                pool.submit(new PathGenerator(getProject(), artifacts, generatePathTask, destination, options.isStripVersions()));
            }

            if (generateVersionsTask != null) {
                getProject().log("Scheduling version properties generation...", Project.MSG_VERBOSE);
                pool.submit(new VersionsGenerator(getProject(), artifacts, generateVersionsTask));
            }

            if (findUpdatesFile != null) {
                getProject().log("Scheduling new versions check...", Project.MSG_VERBOSE);
                pool.submit(new FindUpdates(getProject(), artifacts, findUpdatesFile, options.getServers()));
            }

            if (generateLicenses) {
                Map<String, URI> licenses = new HashMap<String, URI>(pomFutures.size() * 2);
                Set<PomDetails> poms = new HashSet<PomDetails>(pomFutures.size() * 2);

                for (Future<PomDetails> f : pomFutures) {
                    Pair<String, URI> license = f.get().getLicense();
                    licenses.put(license.getKey(), license.getValue());
                    poms.add(f.get());
                }
                getProject().log("Scheduling license creation...", Project.MSG_VERBOSE);
                pool.submit(new LicenseGenerator(getProject(), destination, poms, licenses));
            }

            for (Future<?> f : downloadFutures) {
                f.get();
            }
            getProject().log("Downloads finished...", Project.MSG_VERBOSE);

            if (failOnError) {
                for (Artifact artifact : artifacts) {
                    if ((artifact.getStatus() == Artifact.Status.FAILED) || (artifact.getStatus() == Artifact.Status.DIGEST_MISMATCH)) {
                        throw new BuildException("Failed downloading artifacts (First Failure: " + artifact + ")");
                    }
                }
            }

            if (reportMissingDependencies) {
                getProject().log("Reporting missing dependencies...", Project.MSG_VERBOSE);
                Set<Artifact> requiredArtifacts = new HashSet<Artifact>();
                for (Future<PomDetails> f : pomFutures) {
                    requiredArtifacts.addAll(f.get().getDependentArtifacts());
                }

                Iterator<Artifact> it = requiredArtifacts.iterator();
                while (it.hasNext()) {
                    if (artifacts.contains(it.next())) {
                        it.remove();
                    }
                }

                if (!requiredArtifacts.isEmpty()) {
                    getProject().log("");
                    getProject().log("Required (but missing) transitive dependencies");
                    getProject().log("");
                    for (Artifact a : requiredArtifacts) {
                        getProject().log(a.toString());
                    }
                }
            }

        } catch (Exception e) {
            if (failOnError) {
                getProject().log(e.getMessage(), Project.MSG_ERR);
                throw new BuildException("Failed yanking files", e);
            }
        } finally {
            pool.shutdown();
        }

        getProject().log("Finished.", Project.MSG_VERBOSE);
    }
}
