/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2014 MeBigFatGuy.com
 * Copyright 2013-2014 Dave Brosius
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

import java.net.URL;
import java.util.List;

public class PomDetails {
	private Artifact jarArtifact;
	private List<Artifact> dependentArtifacts;
	private URL license;
	
	public PomDetails(Artifact artifact, List<Artifact> dependencies, URL licenseURL) {
		jarArtifact = artifact;
		dependentArtifacts = dependencies;
		license = licenseURL;
	}

	public Artifact getJarArtifact() {
		return jarArtifact;
	}

	public List<Artifact> getDependentArtifacts() {
		return dependentArtifacts;
	}

	public URL getLicense() {
		return license;
	}
	
	@Override
	public int hashCode() {
		return jarArtifact.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof PomDetails)) {
			return false;
		}
		
		return jarArtifact.equals(((PomDetails) o).jarArtifact);
	}
	
	@Override
	public String toString() {
		return "PomDetails[" + jarArtifact.toString() + ", Dependencies: " + dependentArtifacts.toString() + ", licenseUrl: " + license + "]";
	}
}