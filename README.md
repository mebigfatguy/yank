yank
====

a non transitive maven artifact fetcher for corporate environments.


yank is an ant task that can retrieve maven artifacts from public and private servers, similar to maven and ivy.

However yank, is meant for corporate environments where governance rules are more strict than transitive 
dependency managers allow. yank only pulls down what you ask for, and the the files to be pulled are documented
in an excel spread sheet for easy use of corporate approvals, etc.

You can add this task, as

  	<yank yankFile="${lib.dir}/yank.xls" destination="${lib.dir}">
			<server url="http://repo1.maven.org/maven2"/>
		</yank>

and it will pull new artifacts as needed.

There are a few optional arguments to the yank task that you can add as follows

failOnError                     fails the build if an artifact fails to download     (true/false)       defaults to true
proxyServer                     the url of the proxy server to use                   (String)           defaults to none
reportMissingDependencies       logs transitive dependencies based on the poms       (true/false)       defaults to false
source                          download source artifacts as well                    (true/false)       defaults to false
stripVersions                   save the jar files without version numbers           (true/false)       defaults to false
threadPoolSize                  number of concurrent download threads                (integer)          defaults to 4 * numProcs


the excel spreadsheet is just a normal spread sheet of your own design, so long as there are GroupId, 
ArtifactId and Version columns. More columns may be added for your governance purposes, such as license, reason,
code area, etc, without issue.

