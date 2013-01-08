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
import java.util.List;
import java.util.Map;

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

    public void addConfiguredServer(ServerTask server) {
        servers.add(server.getUrl());
    }

    public void execute() throws BuildException {
        if (!xlsFile.isFile())
            throw new BuildException("Yank (xls) file not specified or invalid: " + xlsFile);
        if (destination.isFile())
            throw new BuildException("Yank destination (" + destination + ") is a file, not a directory");
        if (servers.isEmpty())
            throw new BuildException("No specified nested <server> items found");

        destination.mkdirs();

        try {

            List<Artifact> artifacts = readArtifactList();
        } catch (Exception e) {
            throw new BuildException("Failed yanking files", e);
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


            for (int i = sheet.getFirstRowNum()+1; i <= sheet.getLastRowNum(); ++i) {
                HSSFRow row = sheet.getRow(i);
                HSSFCell cell = row.getCell(columnHeaders.get(ColumnType.GROUP_COLUMN));
                String groupId = cell.getStringCellValue().trim();
                cell = row.getCell(columnHeaders.get(ColumnType.ARTIFACT_COLUMN));
                String artifactId = cell.getStringCellValue().trim();
                cell = row.getCell(columnHeaders.get(ColumnType.VERSION_COLUMN));
                String version = cell.getStringCellValue().trim();

                if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
                    getProject().log("Invalid artifact specified: [groupId: " + groupId + ", artifactId: " + artifactId + ", version: " + version + "]");
                } else {
                    artifacts.add(new Artifact(groupId, artifactId, version));
                }
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

    public static void main(String[] args) {
        YankTask yt = new YankTask();
        Project p = new Project();
        yt.setProject(p);

        yt.setYankFile(new File("/home/dave/dev/yank/sample/yank.xls"));
        yt.setDestination(new File("/home/dave/dev/yank/sample"));
        ServerTask st = new ServerTask();
        st.setUrl("http://repo1.maven.org/maven2");
        yt.addConfiguredServer(st);

        yt.execute();
    }
}
