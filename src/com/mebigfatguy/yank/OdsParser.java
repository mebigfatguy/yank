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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class OdsParser implements SpreadsheetParser {

	private static final String ODS_URI = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
	private static final String CONTENT_STREAM = "content.xml";
	private static final String TABLE_ROW = "table-row";
	private static final String TABLE_CELL = "table-cell";
	private static final String NUMBER_COLUMNS_REPEATED = "number-columns-repeated";
	
	private Project project;
	private File odsFile;
	private List<Artifact> artifacts = new ArrayList<Artifact>();
	
	@Override
	public List<Artifact> getArtifactList(Project proj, File spreadsheet) throws IOException {
		
		project = proj;
		odsFile = spreadsheet;
		
		ZipInputStream zipIs = null;
		InputStream contentStream = null;
		try {
			zipIs = new ZipInputStream(new BufferedInputStream(new FileInputStream(odsFile)));
			contentStream = findContentStream(zipIs);
			XMLReader r = XMLReaderFactory.createXMLReader();
			r.setContentHandler(new OdsHandler());
			r.parse(new InputSource(contentStream));
		} catch (SAXException e) {
			throw new IOException("Failed creating xml reader for ods file (" + odsFile +")", e);
		} finally {
			Closer.close(contentStream);
			Closer.close(zipIs);
		}
		
		return artifacts;
	}

	private InputStream findContentStream(ZipInputStream zipIs) throws IOException {
		ZipEntry entry;
		while ((entry = zipIs.getNextEntry()) != null) {
			if (CONTENT_STREAM.equals(entry.getName())) {
				long size = entry.getSize();
				return new LengthLimitingInputStream(zipIs, (size < 0) ? Long.MAX_VALUE : size);
			}
		}
		
		throw new IOException("ods file has no content stream (" + odsFile +")");
	}
	
	class OdsHandler extends DefaultHandler {

		private int curRow = 0;
		private int curCol = 0;
		private boolean parsingColumnHeaders = true;
		private Map<Integer, ColumnType> columnHeaders = new HashMap<Integer, ColumnType>();
		private StringBuilder contents = new StringBuilder();
		private String groupId = "";
        private String artifactId = "";
        private String type = JAR;
        private String version = "";
        private String classifier = "";
        private int repeatCount = 1;
		
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			contents.setLength(0);
			
			if (TABLE_ROW.equals(localName)) {
				curRow++;
				curCol = 0;

			} else if (TABLE_CELL.equals(localName)) {
				contents.setLength(0);
				String repeats = attributes.getValue(ODS_URI, NUMBER_COLUMNS_REPEATED);
				if (repeats != null) {
					repeatCount = Integer.parseInt(repeats);
				} else {
					repeatCount = 1;
				}
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if (TABLE_ROW.equals(localName)) {
				if (parsingColumnHeaders) {
					if (columnHeaders.size() > 0) {
						if (columnHeaders.size() < 3)
							throw new BuildException("Input yank ods file (" + odsFile + ") does not contains GroupId, ArtifactId, or Version columns");
						parsingColumnHeaders = false;
					}
				} else {
					if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
                    	if (!groupId.isEmpty() || !version.isEmpty()) {
                    		project.log("Row " + curRow + ": Invalid artifact specified: [groupId: " + groupId + ", artifactId: " + artifactId + ", classifier: " + classifier + ", version: " + version + "]");
                    	}
                    } else {
                        artifacts.add(new Artifact(groupId, artifactId, type, classifier, version));
                    }
					
	                artifactId = "";
	                classifier = "";
	                type = JAR;
				}
			} else if (TABLE_CELL.equals(localName)) {
				curCol++;
				String value = contents.toString().trim();
				contents.setLength(0);
				
				if (parsingColumnHeaders) {
					value = value.toLowerCase();
					if (value.startsWith("group")) {
                        columnHeaders.put(curCol, ColumnType.GROUP_COLUMN);
                    } else if (value.startsWith("artifact")) {
                        columnHeaders.put(curCol, ColumnType.ARTIFACT_COLUMN);
                    } else if (value.startsWith("type")) {
                        columnHeaders.put(curCol, ColumnType.TYPE_COLUMN);
                    } else if (value.startsWith("version")) {
                        columnHeaders.put(curCol, ColumnType.VERSION_COLUMN);
                    } else if (value.startsWith("classifier") || value.startsWith("alternate")) {
                        columnHeaders.put(curCol, ColumnType.CLASSIFIER_COLUMN);
                    }
				} else {
					while (repeatCount > 0) {
						ColumnType colType = columnHeaders.get(curCol);
						if (colType != null) {
							switch (colType) {
								case GROUP_COLUMN:
									if (!value.isEmpty()) {
										groupId = value;
									}
									break;
								case ARTIFACT_COLUMN:
									if (!value.isEmpty()) {
										artifactId = value;
									}
									break;
								case TYPE_COLUMN:
									if (!value.isEmpty()) {
										type = value;
									}
									break;
								case VERSION_COLUMN:
									if (!value.isEmpty()) {
										version = value;
									}
									break;
								case CLASSIFIER_COLUMN:
									if (!value.isEmpty()) {
										classifier = value;
									}
									break;
							}
						}
						curCol++;
						repeatCount--;
					}
					curCol--;
				}
			}
		}

		@Override
		public void endDocument() throws SAXException {
			project.log(curRow + " rows read from " + odsFile, Project.MSG_VERBOSE);
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			contents.append(ch, start, length);
		}
	}
}
