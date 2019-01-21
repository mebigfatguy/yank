/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2019 MeBigFatGuy.com
 * Copyright 2013-2019 Dave Brosius
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

import java.net.URI;
import java.util.List;

public class PomDetails {
	private Artifact jarArtifact;
	private List<Artifact> dependentArtifacts;
	private Pair<String, URI> license;
	
	public PomDetails(Artifact artifact, List<Artifact> dependencies, Pair<String, URI> licenseInfo) {
		jarArtifact = artifact;
		dependentArtifacts = dependencies;
		license = licenseInfo;
	}

	public Artifact getJarArtifact() {
		return jarArtifact;
	}

	public List<Artifact> getDependentArtifacts() {
		return dependentArtifacts;
	}

	public Pair<String, URI> getLicense() {
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
		return "PomDetails[" + jarArtifact + ", Dependencies: " + dependentArtifacts + ", license: " + license + "]";
	}
}
