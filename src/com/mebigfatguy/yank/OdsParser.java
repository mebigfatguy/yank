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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tools.ant.Project;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class OdsParser implements SpreadsheetParser {

	private static final String CONTENT_STREAM = "content.xml";
	private static final String TABLE_ROW = "table-row";
	private static final String TABLE_CELL = "table-cell";
	
	@Override
	public List<Artifact> getArtifactList(Project project, File spreadsheet) throws IOException {
		
		ZipInputStream zipIs = null;
		InputStream contentStream = null;
		try {
			zipIs = new ZipInputStream(new BufferedInputStream(new FileInputStream(spreadsheet)));
			contentStream = findContentStream(zipIs);
			XMLReader r = XMLReaderFactory.createXMLReader();
			r.setContentHandler(new OdsHandler());
			r.parse(new InputSource(contentStream));
		} catch (SAXException e) {
			throw new IOException("Failed creating xml reader for ods file", e);
		} finally {
			Closer.close(contentStream);
			Closer.close(zipIs);
		}
		
		throw new IOException("Not implemented yet");
	}

	private InputStream findContentStream(ZipInputStream zipIs) throws IOException {
		ZipEntry entry;
		while ((entry = zipIs.getNextEntry()) != null) {
			if (CONTENT_STREAM.equals(entry.getName())) {
				long size = entry.getSize();
				return new LengthLimitingInputStream(zipIs, (size < 0) ? Long.MAX_VALUE : size);
			}
		}
		
		throw new IOException("ods file has no content stream");
	}
	
	class OdsHandler extends DefaultHandler {

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			if (TABLE_ROW.equals(localName)) {
				
			} else if (TABLE_CELL.equals(localName)) {
				
			}
		}
	}

}
