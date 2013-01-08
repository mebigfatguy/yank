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

public class Artifact {
    public enum Status {UPTODATE, DOWNLOADED, FAILED, UNKNOWN};

    private String groupId;
    private String artifactId;
    private String version;
    private Status status = Status.UNKNOWN;

    public Artifact(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
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


    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public URL toURL(String server) {
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

            url.append(".jar");

            return new URL(url.toString());
        } catch (MalformedURLException mue) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Artifact [groupId=" + groupId + ", artifactId=" + artifactId
                + ", version=" + version + ", status=" + status + "]";
    }
}
