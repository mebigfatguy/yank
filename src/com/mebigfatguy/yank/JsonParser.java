package com.mebigfatguy.yank;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.Project;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class JsonParser implements SpreadsheetParser {

    private Project project;
    private File jsonFile;
    private List<Artifact> artifacts = new ArrayList();

    @Override
    public List<Artifact> getArtifactList(Project proj, File spreadsheet) throws IOException {

        project = proj;
        jsonFile = spreadsheet;

        InputStream jsonIs = null;
        try {
            jsonIs = new BufferedInputStream(new FileInputStream(jsonFile));

            JSONTokener tokenizer = new JSONTokener(jsonIs);
            JSONArray array = new JSONArray(tokenizer);

            String groupId = "";
            String artifactId = "";
            String version = "";
            String type = JAR;
            String classifier = "";
            String digest = "";

            int length = array.length();
            for (int i = 0; i < length; i++) {
                JSONObject obj = array.getJSONObject(i);
                groupId = obj.optString("groupId", groupId);
                artifactId = obj.optString("artifactId");
                version = obj.optString("version", version);
                type = obj.optString("type", JAR);
                classifier = obj.optString("classifier");
                digest = obj.optString("digest");

                if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
                    if (!(groupId.isEmpty() && artifactId.isEmpty() && version.isEmpty())) {
                        if (groupId.isEmpty() || version.isEmpty()) {
                            project.log("Object " + i + ": Invalid artifact specified: [groupId: " + groupId + ", artifactId: " + artifactId + ", classifier: "
                                    + classifier + ", version: " + version + ", digest: " + digest + "]");
                        }
                    }
                } else {
                    artifacts.add(new Artifact(groupId, artifactId, type, classifier, version, digest));
                }
            }

        } catch (JSONException e) {
            throw new IOException("Failed parsing json file (" + jsonFile + ")", e);
        } finally {
            Closer.close(jsonIs);
        }

        return artifacts;
    }
}
