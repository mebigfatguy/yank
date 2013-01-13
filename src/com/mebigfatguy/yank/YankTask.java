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
import java.util.Collections;
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
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

public class YankTask extends Task {

    private enum ColumnType {GROUP_COLUMN, ARTIFACT_COLUMN, VERSION_COLUMN};
    private File xlsFile;
    private File destination;
    private boolean reportMissingDependencies;
    private boolean outputPath;
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

    public void setOutputPath(boolean path) {
        outputPath = path;
    }

    public void setStripVersions(boolean strip) {
        options.setStripVersions(strip);
    }

    public void setSource(boolean sources) {
        options.setYankSources(sources);
    }

    public void setThreadPoolSize(int size) {
        threadPoolSize = size;
    }

    public void addConfiguredServer(ServerTask server) {
        String url = server.getUrl().trim();
        if (!url.endsWith("/"))
            url += "/";
        options.addServer(url);
    }

    public void setProxyServer(String proxy) {
        options.setProxyServer(proxy.trim());
    }

    public void execute() throws BuildException {
        if (!xlsFile.isFile())
            throw new BuildException("Yank (xls) file not specified or invalid: " + xlsFile);
        if (destination.isFile())
            throw new BuildException("Yank destination (" + destination + ") is a file, not a directory");
        if (options.getServers().isEmpty())
            throw new BuildException("No specified nested <server> items found");

        destination.mkdirs();

        ExecutorService pool = Executors.newFixedThreadPool(threadPoolSize);
        try {

            List<Artifact> artifacts = readArtifactList();
            List<Future<?>> downloadFutures = new ArrayList<Future<?>>();
            Project project = getProject();

            for (Artifact artifact : artifacts) {
                downloadFutures.add(pool.submit(new Downloader(project, artifact, destination, options)));
            }

            List<Future<List<Artifact>>> transitiveFutures = new ArrayList<Future<List<Artifact>>>();
            if (reportMissingDependencies) {
                for (Artifact artifact : artifacts) {
                    transitiveFutures.add(pool.submit(new DiscoverTransitives(project, artifact, options)));
                }
            }

            for (Future<?> f : downloadFutures) {
                f.get();
            }

            if (reportMissingDependencies) {
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
                    project.log("");
                    project.log("Required (but missing) transitive dependencies");
                    project.log("");
                    for (Artifact a : requiredArtifacts) {
                        project.log(a.toString());
                    }
                }
            }

            if (failOnError) {
                for (Artifact artifact : artifacts) {
                    if (artifact.getStatus() == Artifact.Status.FAILED) {
                        throw new BuildException("Failed downloading artifacts (" + artifact + ")");
                    }
                }
            }

            if (outputPath) {
                String path = generatePath(artifacts);
                project.log("");
                project.log(path);
            }

        } catch (Exception e) {
            if (failOnError)
                throw new BuildException("Failed yanking files", e);
        } finally {
            pool.shutdown();
        }
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
            String groupId = "";
            String artifactId = "";
            String version = "";

            for (int i = sheet.getFirstRowNum()+1; i <= sheet.getLastRowNum(); ++i) {
                HSSFRow row = sheet.getRow(i);
                if (row != null) {
                    HSSFCell cell = row.getCell(columnHeaders.get(ColumnType.GROUP_COLUMN));
                    if (cell != null) {
                        groupId = cell.getStringCellValue().trim();
                    }

                    cell = row.getCell(columnHeaders.get(ColumnType.ARTIFACT_COLUMN));
                    if (cell != null) {
                        artifactId = cell.getStringCellValue().trim();
                    }

                    cell = row.getCell(columnHeaders.get(ColumnType.VERSION_COLUMN));
                    if (cell != null) {
                        version = cell.getStringCellValue().trim();
                    }

                    if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
                        getProject().log("Invalid artifact specified: [groupId: " + groupId + ", artifactId: " + artifactId + ", version: " + version + "]");
                    } else {
                        artifacts.add(new Artifact(groupId, artifactId, version));
                    }
                }

                artifactId = "";
            }
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
            String value = cell.getStringCellValue().trim().toLowerCase();
            if (value.startsWith("group")) {
                columnHeaders.put(ColumnType.GROUP_COLUMN, i);
            } else if (value.startsWith("artifact")) {
                columnHeaders.put(ColumnType.ARTIFACT_COLUMN, i);
            } else if (value.startsWith("version")) {
                columnHeaders.put(ColumnType.VERSION_COLUMN, i);
            }
            if (columnHeaders.size() == 3) {
                return columnHeaders;
            }
        }

        throw new BuildException("Input yank xls file (" + xlsFile + ") does not contains GroupId, ArtifactId, or Version columns");
    }


    private String generatePath(List<Artifact> artifacts) {
        Collections.sort(artifacts);

        StringBuilder sb = new StringBuilder();
        String eol = System.getProperty("line.separator");
        sb.append("\t<path id=\"yank.classpath\">").append(eol);
        for (Artifact artifact : artifacts) {
            sb.append("\t\t<pathelement location=\"${lib.dir}/").append(artifact.getArtifactId());
            if (!options.isStripVersions()) {
                sb.append('-').append(artifact.getVersion());
            }
            sb.append(".jar\" />").append(eol);
        }
        sb.append("\t<path>");

        return sb.toString();
    }
}
