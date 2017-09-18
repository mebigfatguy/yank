/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2016 MeBigFatGuy.com
 * Copyright 2013-2016 Dave Brosius
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
import java.util.List;

import org.apache.tools.ant.Project;

public class SpreadsheetParserFactory {

    private static final String ODS_EXTENSION = "ods";
    private static final String XLSX_EXTENSION = "xlsx";
    private static final String JSON_EXTENSION = "json";
    private static final String CSV_EXTENSION = "csv";
    private static final String TXT_EXTENSION = "txt";

    private SpreadsheetParserFactory() {

    }

    public static List<Artifact> parse(Project project, File spreadsheet) throws IOException {
        int extPos = spreadsheet.getName().lastIndexOf('.');

        String extension = (extPos < 0) ? "" : spreadsheet.getName().substring(extPos + 1).toLowerCase();

        SpreadsheetParser parser;

        if (ODS_EXTENSION.equals(extension)) {
            parser = new OdsParser();
        } else if (XLSX_EXTENSION.equals(extension)) {
            parser = new XlsxParser();
        } else if (JSON_EXTENSION.equals(extension)) {
            parser = new JsonParser();
        } else if (CSV_EXTENSION.equals(extension)) {
            parser = new CsvParser();
        } else if (TXT_EXTENSION.equals(extension)) {
            parser = new TdfParser();
        } else {
            parser = new XlsParser();
        }

        return parser.getArtifactList(project, spreadsheet);
    }
}
