/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2014MeBigFatGuy.com
 * Copyright 2013-2014Dave Brosius
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

import java.util.ArrayList;
import java.util.List;

public class Options {

    private boolean stripVersions;
    private boolean yankSources;
    private String proxyServer="";
    private List<String> servers = new ArrayList<String>();
    private boolean separateClassifierTypes = false;

    public boolean isStripVersions() {
        return stripVersions;
    }

    public void setStripVersions(boolean stripVersions) {
        this.stripVersions = stripVersions;
    }

    public String getProxyServer() {
        return proxyServer;
    }

    public void setProxyServer(String proxyServer) {
        this.proxyServer = proxyServer;
    }

    public boolean isYankSources() {
        return yankSources;
    }

    public void setYankSources(boolean yankSources) {
        this.yankSources = yankSources;
    }

    public List<String> getServers() {
        return servers;
    }

    public void addServer(String server) {
        servers.add(server);
    }

	public boolean isSeparateClassifierTypes() {
		return separateClassifierTypes;
	}

	public void setSeparateClassifierTypes(boolean separateClassifierTypes) {
		this.separateClassifierTypes = separateClassifierTypes;
	}
}
