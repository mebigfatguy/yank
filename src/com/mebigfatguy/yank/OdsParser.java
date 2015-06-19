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

public class OdsParser implements SpreadsheetParser {

	private static final String CONTENT_STREAM = "/content.xml";
	@Override
	public List<Artifact> getArtifactList(Project project, File spreadsheet) throws IOException {
		
		try (ZipInputStream zipIs = new ZipInputStream(new BufferedInputStream(new FileInputStream(spreadsheet)));
			 InputStream contentStream = findContentStream(zipIs)) {
		}
		
		throw new IOException("Not implemented yet");
	}

	private InputStream findContentStream(ZipInputStream zipIs) throws IOException {
		ZipEntry entry;
		while ((entry = zipIs.getNextEntry()) != null) {
			if (CONTENT_STREAM.equals(entry.getName())) {
				return new LengthLimitingInputStream(zipIs, entry.getSize());
			}
		}
		
		throw new IOException("ods file has no content stream");
	}

}
