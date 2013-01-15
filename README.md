yank
====

a non transitive maven artifact fetcher for corporate environments.


yank is an ant task that can retrieve maven artifacts from public and private servers, similar to maven and ivy.

However yank is meant for corporate environments where governance rules are more strict than transitive 
dependency managers allow. yank only pulls down what you ask for, and the the files to be pulled are documented
in an excel spread sheet for easy use of corporate approvals, etc.

You can add this task, as

    <yank yankFile="${lib.dir}/yank.xls" destination="${lib.dir}">
            <server url="http://repo1.maven.org/maven2"/>
        </yank>

and it will pull new artifacts as needed.

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
    <generatePath pathXmlFile="${sample.dir}/yank_build.xml" classpathName="yank.path" libraryDirName="$${lib.dir}" />
    
This will create an ant xml project file located at pathXmlFile (${sample.dir}/yank_build.xml) like
    <path name="yank.path">
        <pathelement location="${lib.dir}/asm.jar" />
    </path
    
With pathlements for each jar specified.

the excel spreadsheet is just a normal spread sheet of your own design, so long as there are GroupId, 
ArtifactId and Version columns. More columns may be added for your governance purposes, such as license, reason,
code area, etc, without issue.

