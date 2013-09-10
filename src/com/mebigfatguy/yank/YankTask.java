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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

public class YankTask extends Task {

    private enum ColumnType {GROUP_COLUMN, ARTIFACT_COLUMN, ALTERNATE, VERSION_COLUMN};
    private File xlsFile;
    private File destination;
    private boolean reportMissingDependencies;
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

    public void setStripVersions(boolean strip) {
        options.setStripVersions(strip);
    }

    public void setSource(boolean sources) {
        options.setYankSources(sources);
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

            final List<Artifact> artifacts = readArtifactList();
            List<Future<?>> downloadFutures = new ArrayList<Future<?>>();

            for (Artifact artifact : artifacts) {
                downloadFutures.add(pool.submit(new Downloader(getProject(), artifact, destination, options)));
            }

            List<Future<List<Artifact>>> transitiveFutures = new ArrayList<Future<List<Artifact>>>();
            if (reportMissingDependencies) {
                for (Artifact artifact : artifacts) {
                    transitiveFutures.add(pool.submit(new DiscoverTransitives(getProject(), artifact, options)));
                }
            }

            if (generatePathTask != null) {
                pool.submit(new Runnable() {
                    public void run() {
                        generatePath(artifacts);
                    }
                });
            }
            
            if (generateVersionsTask != null) {
                pool.submit(new Runnable() {
                    public void run() {
                        generateVersions(artifacts);
                    }
                });
            }  

            for (Future<?> f : downloadFutures) {
                f.get();
            }

            if (failOnError) {
                for (Artifact artifact : artifacts) {
                    if (artifact.getStatus() == Artifact.Status.FAILED) {
                        throw new BuildException("Failed downloading artifacts (First Failure: " + artifact + ")");
                    }
                }
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
            Integer alternateColumn = columnHeaders.get(ColumnType.ALTERNATE);
            String groupId = "";
            String artifactId = "";
            String version = "";
            String alternate = "";

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
                        if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                            version = String.valueOf(cell.getNumericCellValue());
                        } else {
                            version = cell.getStringCellValue().trim();
                        }
                    }
                    
                    cell = (alternateColumn != null) ? row.getCell(alternateColumn.intValue()) : null;
                    if (cell != null) {
                        alternate = cell.getStringCellValue().trim();
                    }

                    if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
                        getProject().log("Invalid artifact specified: [groupId: " + groupId + ", artifactId: " + artifactId + ", alternate: " + alternate + ", version: " + version + "]");
                    } else {
                        artifacts.add(new Artifact(groupId, artifactId, alternate, version));
                    }
                }

                artifactId = "";
                alternate = "";
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
            } else if (value.startsWith("alternate")) {
                columnHeaders.put(ColumnType.ALTERNATE,  i);
            }
            if (columnHeaders.size() == 4) {
                return columnHeaders;
            }
        }
        
        if (columnHeaders.size() >= 3)
            return columnHeaders;

        throw new BuildException("Input yank xls file (" + xlsFile + ") does not contains GroupId, ArtifactId, or Version columns");
    }


    private void generatePath(List<Artifact> artifacts) {
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new BufferedWriter(new FileWriter(generatePathTask.getPathXmlFile())));
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
                if (!options.isStripVersions()) {
                    pw.print('-');
                    pw.print(artifact.getVersion());
                }
                pw.println(".jar\" />");
            }
            pw.println("\t</path>");
            pw.println("</project>");
        } catch (Exception e) {
            getProject().log("Failed generating classpath " + generatePathTask.getClasspathName() + " in file " + generatePathTask.getPathXmlFile(), e, Project.MSG_ERR);
        } finally {
            Closer.close(pw);
        }
    }
    
    private void generateVersions(List<Artifact> artifacts) {
        PrintWriter pw = null;
        Project proj = getProject();
        try {
            pw = new PrintWriter(new BufferedWriter(new FileWriter(generateVersionsTask.getPropertyFileName())));
            for (Artifact artifact : artifacts) {
                pw.println(artifact.getArtifactId() + ".version = " + artifact.getVersion());
                proj.setProperty(artifact.getArtifactId() + ".version", artifact.getVersion());
            }
        } catch (Exception e) {
            proj.log("Failed generating versions property file " + generateVersionsTask.getPropertyFileName(), e, Project.MSG_ERR);
        } finally {
            Closer.close(pw);
        }
    }

    public static void main(String[] args) {
        YankTask yt = new YankTask();
        Project p = new Project();
        yt.setProject(p);

        yt.setYankFile(new File("/home/dave/dev/yank/sample/yank.xls"));
        yt.setDestination(new File("/home/dave/dev/yank/sample"));
        yt.setThreadPoolSize(8);
        yt.setStripVersions(true);
        yt.setProxyServer("localhost:8181");

        ServerTask st = new ServerTask();
        st.setUrl("http://repo1.maven.org/maven2");
        yt.addConfiguredServer(st);

        GeneratePathTask pt = new GeneratePathTask();
        pt.setClasspathName("yank.path");
        pt.setPathXmlFile(new File("/home/dave/dev/yank/sample/yank_build.xml"));
        pt.setLibraryDirName("${lib.dir}");
        yt.addConfiguredGeneratePath(pt);

        yt.execute();
    }
}
