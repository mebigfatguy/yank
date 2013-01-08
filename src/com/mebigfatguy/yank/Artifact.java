package com.mebigfatguy.yank;

public class Artifact {
    private String groupId;
    private String artifactId;
    private String version;

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

    @Override
    public String toString() {
        return "Artifact [groupId=" + groupId + ", artifactId=" + artifactId
                + ", version=" + version + "]";
    }
}
