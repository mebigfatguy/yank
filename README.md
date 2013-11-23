yank
====

a non transitive maven artifact fetcher for corporate environments.


yank is an ant task that can retrieve maven artifacts from public and private servers, similar to maven and ivy.

However yank is meant for corporate environments where governance rules are more strict than transitive 
dependency managers allow. yank only pulls down what you ask for, and the the files to be pulled are documented
in an excel spread sheet for easy use of corporate approvals, etc.

You can add this task, as
<pre>
    &lt;yank yankFile="${lib.dir}/yank.xls" destination="${lib.dir}"&gt;
            &lt;server url="http://repo1.maven.org/maven2"/&gt;
    &lt;/yank&gt;
</pre>

and it will pull new artifacts as needed. You can list as many servers as desired each in its own element.

There are a few optional attributes to the yank task that you can add as follows

<table>
   <tr>
      <th>Property</th>
      <th>Description</th>
      <th>Value</th>
      <th>Default</th>
   </tr>
   <tr>
      <td>failOnError</td>
      <td>fails the build if an artifact fails to download</td>
      <td>(true/false)</td>
      <td>true</td>
   </tr>
   <tr>
      <td>proxyServer</td>
      <td>the url of the proxy server to use</td>
      <td>(String)</td>
      <td>blank</td>
   </tr>
   <tr>
      <td>reportMissingDependencies</td>
      <td>logs transitive dependencies based on the poms</td>
      <td>(true/false)</td>
      <td>false</td>
   </tr>  
   <tr>
      <td>findUpdatesFile</td>
      <td>generate a file with jars that have updated versions not being used</td>
      <td>(file)</td>
      <td>null</td>  
   <tr>
      <td>source</td>
      <td>download source artifacts as well</td>
      <td>(true/false)</td>
      <td>false</td>
   </tr>    
   <tr>
      <td>stripVersions</td>
      <td>save the jar files without version numbers</td>
      <td>(true/false)</td>
      <td>false</td>
   </tr>  
   <tr>
      <td>threadPoolSize</td>
      <td>number of concurrent download threads</td>
      <td>(integer)</td>
      <td>4 * numProcessors</td>
   </tr>
</table>

In addition, you can add a sub element to generate an ant path element, such as
<pre>
    &lt;generatePath pathXmlFile="${sample.dir}/yank_build.xml" classpathName="yank.path" libraryDirName="$${lib.dir}" /&gt;
</pre>

This will dynamically populate a classpath element in your project with reference 'yank.path' that can be used in <java> tasks etc.
The pathXMLFile attribute is optional, but if specified, will also produce an ant xml project file located at 
pathXmlFile (${sample.dir}/yank_build.xml) like
<pre>
    &lt;path name="yank.path"&gt;
        &lt;pathelement location="${lib.dir}/asm.jar" /&gt;
    &lt;/path&gt;
</pre>
    
With path elements for each jar specified.

You can also add a sub element to generate a properties file containing constants for all the jar version numbers, such as
<pre>
    &lt;generateVersions propertyFileName="${basedir}/version.properties" /&gt;
</pre>

As for the yank.xls file, the excel spreadsheet is just a normal spread sheet of your own design, so long as there are GroupId, 
ArtifactId and Version columns. You may also specify an Alternate column, which can download jars with names after the version such as natives.
Note that sources jars are automatically handled and you need not use the alternate column for this purpose (See the source attribute above).
More columns may be added for your governance purposes, such as license, reason, code area, etc, without issue. 
If values for a column are not filled in, the previous value is pulled down from above.

Below is a simplistic example of a build.xml file that uses yank to manage dependencies
<pre>
    &lt;project name="ty" default="jar" xmlns:yank="antlib:com.mebigfatguy.yank"&gt;
        &lt;property file="build.properties"/&gt;
        &lt;property name="src.dir" value="${basedir}/src" /&gt;
        &lt;property name="classes.dir" value="${basedir}/classes" /&gt;
        &lt;property name="lib.dir" value="${basedir}/lib" /&gt;
        &lt;target name="clean" description="removes all generated collateral"&gt;
            &lt;delete dir="${classes.dir}" /&gt;
        &lt;/target&gt;
        &lt;target name="yank" description="fetch 3rdparty jars from maven central"&gt;
            &lt;mkdir dir="${lib.dir}" /&gt;
            &lt;yank:yank yankFile="${basedir}/yank.xls" destination="${lib.dir}" source="true"&gt;
                &lt;server url="http://repo1.maven.org/maven2"/&gt;
                &lt;generatePath classpathName="ty.classpath" libraryDirName="$${lib.dir}" /&gt;
            &lt;/yank:yank&gt;
        &lt;/target&gt;
        &lt;target name="-init" depends="yank" description="prepares repository for a build"&gt;
            &lt;mkdir dir="${classes.dir}" /&gt;
        &lt;/target&gt;
        &lt;target name="compile" depends="-init" description="compiles java files"&gt;
            &lt;javac srcdir="${src.dir}" destdir="${classes.dir}"&gt;
                 &lt;classpath refid="ty.classpath" /&gt;
            &lt;/javac&gt;
        &lt;/target&gt;
        &lt;target name="jar" depends="compile" description="produces the ty jar file"&gt;
            &lt;jar destfile="${basedir}/try.jar"&gt;
                &lt;fileset dir="${classes.dir}"&gt;
                    &lt;include name="**/*.class" /&gt;
                &lt;/fileset&gt;
            &lt;/jar&gt;  
        &lt;/target&gt;
    &lt;/project&gt;
<pre>

