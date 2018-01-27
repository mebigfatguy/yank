/*
 * yank - a maven artifact fetcher ant task
 * Copyright 2013-2018 MeBigFatGuy.com
 * Copyright 2013-2018 Dave Brosius
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

import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

public class GeneratePathTask extends Task {

    private File pathXmlFile;
    private String classpathName;

    public File getPathXmlFile() {
        return pathXmlFile;
    }

    public void setPathXmlFile(File pathXmlFile) {
        this.pathXmlFile = pathXmlFile;
    }

    public String getClasspathName() {
        return classpathName;
    }

    public void setClasspathName(String classpathName) {
        this.classpathName = classpathName;
    }

    @Override
    public void execute() {
        throw new BuildException("GeneratePathTask not meant to be executed");
    }
}
