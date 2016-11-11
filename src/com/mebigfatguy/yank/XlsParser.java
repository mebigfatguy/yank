/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2015 MeBigFatGuy.com
 * Copyright 2013-2015 Dave Brosius
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
import java.util.Locale;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

public class XlsParser implements SpreadsheetParser {

    private File xlsFile;

    @Override
    public List<Artifact> getArtifactList(Project project, File spreadsheet) throws IOException {
        xlsFile = spreadsheet;

        BufferedInputStream bis = null;
        HSSFWorkbook workBook = null;
        List<Artifact> artifacts = new ArrayList<>();

        try {
            bis = new BufferedInputStream(new FileInputStream(xlsFile));
            workBook = new HSSFWorkbook(bis);

            HSSFSheet sheet = workBook.getSheetAt(0);

            Map<ColumnType, Integer> columnHeaders = getColumnInfo(sheet);
            Integer typeColumn = columnHeaders.get(ColumnType.TYPE_COLUMN);
            Integer classifierColumn = columnHeaders.get(ColumnType.CLASSIFIER_COLUMN);
            Integer digestColumn = columnHeaders.get(ColumnType.DIGEST_COLUMN);
            String groupId = "";
            String artifactId = "";
            String type = JAR;
            String version = "";
            String classifier = "";
            String digest = "";

            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); ++i) {
                HSSFRow row = sheet.getRow(i);
                if (row != null) {
                    HSSFCell cell = row.getCell(columnHeaders.get(ColumnType.GROUP_COLUMN).intValue());
                    if (cell != null) {
                        String gId = cell.getStringCellValue().trim();
                        if (!gId.isEmpty()) {
                            groupId = gId;
                        }
                    }

                    cell = row.getCell(columnHeaders.get(ColumnType.ARTIFACT_COLUMN).intValue());
                    if (cell != null) {
                        String aId = cell.getStringCellValue().trim();
                        if (!aId.isEmpty()) {
                            artifactId = aId;
                        }
                    }

                    cell = row.getCell(columnHeaders.get(ColumnType.VERSION_COLUMN).intValue());
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

                    cell = (typeColumn != null) ? row.getCell(typeColumn.intValue()) : null;
                    if (cell != null) {
                        type = cell.getStringCellValue().trim();
                    }

                    cell = (classifierColumn != null) ? row.getCell(classifierColumn.intValue()) : null;
                    if (cell != null) {
                        classifier = cell.getStringCellValue().trim();
                    }

                    cell = (digestColumn != null) ? row.getCell(digestColumn.intValue()) : null;
                    if (cell != null) {
                        digest = cell.getStringCellValue().trim();
                    }

                    if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
                        if (groupId.isEmpty() || version.isEmpty()) {
                            project.log("Row " + row.getRowNum() + ": Invalid artifact specified: [groupId: " + groupId + ", artifactId: " + artifactId
                                    + ", classifier: " + classifier + ", version: " + version + ", digest: " + digest + "]");
                        }
                    } else {
                        artifacts.add(new Artifact(groupId, artifactId, type, classifier, version, digest));
                    }
                }

                artifactId = "";
                classifier = "";
                digest = "";
                type = JAR;
            }

            project.log(sheet.getLastRowNum() + " rows read from " + xlsFile, Project.MSG_VERBOSE);
        } finally {
            if (workBook != null) {
                workBook.close();
            }
            Closer.close(bis);
        }

        return artifacts;
    }

    private Map<ColumnType, Integer> getColumnInfo(HSSFSheet sheet) {
        int firstRow = sheet.getFirstRowNum();
        HSSFRow row = sheet.getRow(firstRow);

        Map<ColumnType, Integer> columnHeaders = new EnumMap<>(ColumnType.class);

        for (int i = row.getFirstCellNum(); i <= row.getLastCellNum(); ++i) {
            HSSFCell cell = row.getCell(i);

            if (cell != null) {
                String value = cell.getStringCellValue();
                if (value != null) {
                    Integer colNum = Integer.valueOf(i);
                    value = value.trim().toLowerCase(Locale.ENGLISH);
                    if (value.startsWith("group")) {
                        columnHeaders.put(ColumnType.GROUP_COLUMN, colNum);
                    } else if (value.startsWith("artifact")) {
                        columnHeaders.put(ColumnType.ARTIFACT_COLUMN, colNum);
                    } else if (value.startsWith("type")) {
                        columnHeaders.put(ColumnType.TYPE_COLUMN, colNum);
                    } else if (value.startsWith("version")) {
                        columnHeaders.put(ColumnType.VERSION_COLUMN, colNum);
                    } else if (value.startsWith("classifier") || value.startsWith("alternate")) {
                        columnHeaders.put(ColumnType.CLASSIFIER_COLUMN, colNum);
                    } else if (value.startsWith("digest")) {
                        columnHeaders.put(ColumnType.DIGEST_COLUMN, colNum);
                    }

                    if (columnHeaders.size() == 6) {
                        return columnHeaders;
                    }
                }
            }
        }

        if (columnHeaders.size() >= 3) {
            return columnHeaders;
        }

        throw new BuildException("Input yank xls file (" + xlsFile + ") does not contains GroupId, ArtifactId, or Version columns");
    }
}
