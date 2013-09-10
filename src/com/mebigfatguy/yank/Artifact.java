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

import java.net.MalformedURLException;
import java.net.URL;

public class Artifact implements Comparable<Artifact> {
    public enum Status {UPTODATE, DOWNLOADED, FAILED, UNKNOWN};

    private String groupId;
    private String artifactId;
    private String version;
    private String alternate;
    private Status status = Status.UNKNOWN;

    public Artifact(String groupId, String artifactId, String alternate, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.alternate = alternate;
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }
    
    public String getAlternate() {
        return alternate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public int hashCode() {
        return groupId.hashCode() ^ artifactId.hashCode() ^ alternate.hashCode() ^ version.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Artifact))
            return false;

        Artifact that = (Artifact) o;
        return groupId.equals(that.groupId) && artifactId.equals(that.artifactId) && alternate.equals(that.alternate) && version.equals(that.version);
    }

    public int compareTo(Artifact a) {
        int cmp = groupId.compareTo(a.groupId);
        if (cmp != 0) {
            return cmp;
        }

        cmp = artifactId.compareTo(a.artifactId);
        if (cmp != 0) {
            return cmp;
        }
        
        cmp = alternate.compareTo(a.alternate);
        if (cmp != 0) {
            return cmp;
        };

        return version.compareTo(a.version);
    }

    public URL toURL(String server) {
        return toURL(server, ".jar");
    }

    public URL pomURL(String server) {
        return toURL(server, ".pom");
    }

    public URL srcURL(String server) {
        return toURL(server, "-sources.jar");
    }

    private URL toURL(String server, String extension) {
        try {
            StringBuilder url = new StringBuilder(server);

            url.append(groupId.replace('.', '/'));
            url.append('/');

            url.append(artifactId);
            url.append('/');

            url.append(version);
            url.append('/');

            url.append(artifactId);
            url.append('-');

            url.append(version);
            
            if (!alternate.isEmpty()) {
                url.append('-').append(alternate);
            }
                
            url.append(extension);

            return new URL(url.toString());
        } catch (MalformedURLException mue) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Artifact [groupId=" + groupId + ", artifactId=" + artifactId
                + ", alternate=" + alternate + ", version=" + version + ", status="
                + status + "]";
    }
}
