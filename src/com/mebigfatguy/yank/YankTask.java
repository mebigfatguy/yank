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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

public class YankTask extends Task {

    static final String SOURCE_CLASSIFIER = "sources";

    private enum ColumnType {GROUP_COLUMN, ARTIFACT_COLUMN, CLASSIFIER, VERSION_COLUMN};
    private File xlsFile;
    private File destination;
    private boolean reportMissingDependencies;
    private File findUpdatesFile;
    private GeneratePathTask generatePathTask;
    private GenerateVersionsTask generateVersionsTask;
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

    public void setThreadPoolSize(int size) {
        threadPoolSize = size;
    }

    public void addConfiguredGeneratePath(GeneratePathTask gpTask) {
        generatePathTask = gpTask;
    }

    public void addConfiguredServer(ServerTask server) {
        String url = server.getUrl().trim();
        if (!url.endsWith("/"))
            url += "/";
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
    
    public void execute() throws BuildException {
        getProject().log("Checking attributes...", Project.MSG_VERBOSE);
        if (!xlsFile.isFile())
            throw new BuildException("Yank (xls) file not specified or invalid: " + xlsFile);
        if (destination.isFile())
            throw new BuildException("Yank destination (" + destination + ") is a file, not a directory");
        if (options.getServers().isEmpty())
            throw new BuildException("No specified nested <server> items found");

        if (!destination.mkdirs() && !destination.exists())
            throw new BuildException("Failed creating destination directory: " + destination);

        ExecutorService pool = Executors.newFixedThreadPool(threadPoolSize);
        try {

            getProject().log("Reading artifact list...", Project.MSG_VERBOSE);
            final List<Artifact> artifacts = readArtifactList();
            List<Future<?>> downloadFutures = new ArrayList<Future<?>>(artifacts.size());

            getProject().log("Scheduling downloaders...", Project.MSG_VERBOSE);
            for (Artifact artifact : artifacts) {
                downloadFutures.add(pool.submit(new Downloader(getProject(), artifact, destination, options)));
                if (options.isYankSources() && (artifact.getClassifier().isEmpty())) {
                    Artifact sourceArtifact = new Artifact(artifact.getGroupId(), artifact.getArtifactId(), SOURCE_CLASSIFIER, artifact.getVersion());
                    downloadFutures.add(pool.submit(new Downloader(getProject(), sourceArtifact, destination, options)));
                }
            }

            List<Future<List<Artifact>>> transitiveFutures = null;
            if (reportMissingDependencies) {
                getProject().log("Scheduling missing dependencies check...", Project.MSG_VERBOSE);
                transitiveFutures = new ArrayList<Future<List<Artifact>>>(artifacts.size());
                for (Artifact artifact : artifacts) {
                    transitiveFutures.add(pool.submit(new DiscoverTransitives(getProject(), artifact, options)));
                }
            }

            if (generatePathTask != null) {
                getProject().log("Scheduling path generation task...", Project.MSG_VERBOSE);
                pool.submit(new PathGenerator(getProject(), artifacts, generatePathTask, options.isStripVersions()));
            }
            
            if (generateVersionsTask != null) {
                getProject().log("Scheduling version properties generation...", Project.MSG_VERBOSE);
                pool.submit(new VersionsGenerator(getProject(), artifacts, generateVersionsTask));
            }  
            
            if (findUpdatesFile != null) {
                getProject().log("Scheduling new versions check...", Project.MSG_VERBOSE);
                pool.submit(new FindUpdates(getProject(), artifacts, findUpdatesFile, options.getServers()));
            }

            for (Future<?> f : downloadFutures) {
                f.get();
            }
            getProject().log("Downloads finished...", Project.MSG_VERBOSE);

            if (failOnError) {
                for (Artifact artifact : artifacts) {
                    if (artifact.getStatus() == Artifact.Status.FAILED) {
                        throw new BuildException("Failed downloading artifacts (First Failure: " + artifact + ")");
                    }
                }
            }

            if (reportMissingDependencies) {
                getProject().log("Reporting missing dependencies...", Project.MSG_VERBOSE);
                Set<Artifact> requiredArtifacts = new HashSet<Artifact>();
                for (Future<List<Artifact>> f : transitiveFutures) {
                    requiredArtifacts.addAll(f.get());
                }

                Iterator<Artifact> it = requiredArtifacts.iterator();
                while (it.hasNext()) {
                    if (artifacts.contains(it.next()))
                        it.remove();
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

    private List<Artifact> readArtifactList() throws IOException {
        BufferedInputStream bis = null;
        List<Artifact> artifacts = new ArrayList<Artifact>();

        try {
            bis = new BufferedInputStream(new FileInputStream(xlsFile));
            POIFSFileSystem poifs = new POIFSFileSystem(bis);
            HSSFWorkbook workBook = new HSSFWorkbook(poifs);

            HSSFSheet sheet = workBook.getSheetAt(0);

            Map<ColumnType, Integer> columnHeaders = getColumnInfo(sheet);
            Integer classifierColumn = columnHeaders.get(ColumnType.CLASSIFIER);
            String groupId = "";
            String artifactId = "";
            String version = "";
            String classifier = "";

            for (int i = sheet.getFirstRowNum()+1; i <= sheet.getLastRowNum(); ++i) {
                HSSFRow row = sheet.getRow(i);
                if (row != null) {
                    HSSFCell cell = row.getCell(columnHeaders.get(ColumnType.GROUP_COLUMN));
                    if (cell != null) {
                        String gId = cell.getStringCellValue().trim();
                        if (!gId.isEmpty()) {
                            groupId = gId;
                        }
                    }

                    cell = row.getCell(columnHeaders.get(ColumnType.ARTIFACT_COLUMN));
                    if (cell != null) {
                        String aId = cell.getStringCellValue().trim();
                        if (!aId.isEmpty()) {
                            artifactId = aId;
                        }
                    }

                    cell = row.getCell(columnHeaders.get(ColumnType.VERSION_COLUMN));
                    if (cell != null) {
                        String v;
                        if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                            v = String.valueOf(cell.getNumericCellValue());
                        } else {
                            v = cell.getStringCellValue().trim();
                        }
                        if (!v.isEmpty()) {
                            version = v;
                        }
                    }
                    
                    cell = (classifierColumn != null) ? row.getCell(classifierColumn.intValue()) : null;
                    if (cell != null) {
                        classifier = cell.getStringCellValue().trim();
                    }

                    if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
                        getProject().log("Row " + row.getRowNum() + ": Invalid artifact specified: [groupId: " + groupId + ", artifactId: " + artifactId + ", classifier: " + classifier + ", version: " + version + "]");
                    } else {
                        artifacts.add(new Artifact(groupId, artifactId, classifier, version));
                    }
                }

                artifactId = "";
                classifier = "";
            }
            
            getProject().log(sheet.getLastRowNum() + " rows read from " + xlsFile, Project.MSG_VERBOSE);
        } finally {
            Closer.close(bis);
        }

        return artifacts;
    }

    private Map<ColumnType, Integer> getColumnInfo(HSSFSheet sheet) {
        int firstRow = sheet.getFirstRowNum();
        HSSFRow row = sheet.getRow(firstRow);

        Map<ColumnType, Integer> columnHeaders = new EnumMap<ColumnType, Integer>(ColumnType.class);

        for (int i = row.getFirstCellNum(); i <= row.getLastCellNum(); ++i) {
            HSSFCell cell = row.getCell(i);
            
            if (cell != null) {
                String value = cell.getStringCellValue();
                if (value != null) {
                    value = value.trim().toLowerCase();
                    if (value.startsWith("group")) {
                        columnHeaders.put(ColumnType.GROUP_COLUMN, i);
                    } else if (value.startsWith("artifact")) {
                        columnHeaders.put(ColumnType.ARTIFACT_COLUMN, i);
                    } else if (value.startsWith("version")) {
                        columnHeaders.put(ColumnType.VERSION_COLUMN, i);
                    } else if (value.startsWith("classifier") || value.startsWith("alternate")) {
                        columnHeaders.put(ColumnType.CLASSIFIER,  i);
                    }
                    if (columnHeaders.size() == 4) {
                        return columnHeaders;
                    }
                }
            }
        }
        
        if (columnHeaders.size() >= 3)
            return columnHeaders;

        throw new BuildException("Input yank xls file (" + xlsFile + ") does not contains GroupId, ArtifactId, or Version columns");
    }
}
