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
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.ICsvListReader;
import org.supercsv.prefs.CsvPreference;

public abstract class SuperParser implements SpreadsheetParser {

    public abstract CsvPreference getPreferences();

    @Override
    public List<Artifact> getArtifactList(Project project, File spreadsheet) throws IOException {
        ICsvListReader r = null;

        try {
            r = new CsvListReader(Files.newBufferedReader(spreadsheet.toPath()), getPreferences());

            Map<ColumnType, Integer> columnHeaders = getColumnInfo(r, spreadsheet);

            Integer typeColumn = columnHeaders.get(ColumnType.TYPE_COLUMN);
            Integer classifierColumn = columnHeaders.get(ColumnType.CLASSIFIER_COLUMN);
            Integer digestColumn = columnHeaders.get(ColumnType.DIGEST_COLUMN);
            String groupId = "";
            String artifactId = "";
            String type = JAR;
            String version = "";
            String classifier = "";
            String digest = "";

            List<Artifact> artifacts = new ArrayList<Artifact>();

            List<String> row;
            int rowNum = 1;
            while ((row = r.read()) != null) {

                String cell = row.get(columnHeaders.get(ColumnType.GROUP_COLUMN).intValue());
                if (cell != null) {
                    String gId = cell.trim();
                    if (!gId.isEmpty()) {
                        groupId = gId;
                    }
                }

                cell = row.get(columnHeaders.get(ColumnType.ARTIFACT_COLUMN).intValue());
                if (cell != null) {
                    String aId = cell.trim();
                    if (!aId.isEmpty()) {
                        artifactId = aId;
                    }
                }

                cell = row.get(columnHeaders.get(ColumnType.VERSION_COLUMN).intValue());
                if (cell != null) {
                    String v = cell.trim();
                    if (!v.isEmpty()) {
                        version = v;
                    }
                }

                cell = (typeColumn != null) ? row.get(typeColumn.intValue()) : null;
                if (cell != null) {
                    type = cell.trim();
                }

                cell = (classifierColumn != null) ? row.get(classifierColumn.intValue()) : null;
                if (cell != null) {
                    classifier = cell.trim();
                }

                cell = (digestColumn != null) ? row.get(digestColumn.intValue()) : null;
                if (cell != null) {
                    digest = cell.trim();
                }

                if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
                    if (!(groupId.isEmpty() && artifactId.isEmpty() && version.isEmpty())) {
                        if (groupId.isEmpty() || version.isEmpty()) {
                            project.log("Row " + rowNum + ": Invalid artifact specified: [groupId: " + groupId + ", artifactId: " + artifactId
                                    + ", classifier: " + classifier + ", version: " + version + ", digest: " + digest + "]");
                        }
                    }
                } else {
                    artifacts.add(new Artifact(groupId, artifactId, type, classifier, version, digest));
                }

                artifactId = "";
                classifier = "";
                digest = "";
                type = JAR;
                rowNum++;
            }

            return artifacts;
        } finally {
            Closer.close(r);
        }
    }

    private Map<ColumnType, Integer> getColumnInfo(ICsvListReader r, File csvFile) {

        try {
            final String[] headers = r.getHeader(true);
            Map<ColumnType, Integer> columnHeaders = new EnumMap<ColumnType, Integer>(ColumnType.class);

            for (int i = 0; i < headers.length; ++i) {

                if (headers[i] != null) {
                    Integer colNum = Integer.valueOf(i);
                    String value = headers[i].trim().toLowerCase(Locale.ENGLISH);
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

            if (columnHeaders.size() >= 3) {
                return columnHeaders;
            }

            throw new BuildException("Input yank csv file (" + csvFile + ") does not contains GroupId, ArtifactId, or Version columns");
        } catch (IOException e) {
            throw new BuildException("Input yank csv file (" + csvFile + ") does not have a header row containing GroupId, ArtifactId, or Version columns");
        }
    }
}
