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
package com.mebigfatguy.yank.test;

import java.io.File;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Path;

import com.mebigfatguy.yank.GeneratePathTask;
import com.mebigfatguy.yank.ServerTask;
import com.mebigfatguy.yank.YankTask;

public class Main {

    public static void main(String[] args) {
        YankTask yt = new YankTask();
        Project p = new Project();
        yt.setProject(p);
        
        p.setProperty("lib.dir", p.getBaseDir() + "/test/lib");

        yt.setYankFile(new File("/home/dave/dev/yank/sample/yank.xls"));
        yt.setDestination(new File("/home/dave/dev/yank/sample"));
        yt.setThreadPoolSize(8);
        yt.setStripVersions(true);

        ServerTask st = new ServerTask();
        st.setUrl("http://repo1.maven.org/maven2");
        yt.addConfiguredServer(st);

        GeneratePathTask pt = new GeneratePathTask();
        pt.setClasspathName("yank.path");
        pt.setPathXmlFile(new File("/home/dave/dev/yank/sample/yank_build.xml"));
        pt.setLibraryDirName("${lib.dir}");
        yt.addConfiguredGeneratePath(pt);
        yt.setFindUpdatesFile(new File("/home/dave/dev/yank/sample/updates.txt"));

        yt.execute();
        
        Java jt = new Java();
        jt.setClasspath((Path) p.getReference("yank.path"));
    }
}
