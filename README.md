yank
====

a non transitive maven artifact fetcher for corporate environments.


yank is an ant task that can retrieve maven artifacts from public and private servers, similar to maven and ivy.

However yank, is meant for corporate environments where governance rules are more strict than transitive 
dependency managers allow. yank only pulls down what you ask for, and the the files to be pulled are documented
in an excel spread sheet for easy use of corporate approvals, etc.

You can add this task, as

  	<yank yankFile="${lib.dir}/yank.xls" destination="${lib.dir}" stripVersions="true" reportMissingDependencies="true" source="true">
			<server url="http://repo1.maven.org/maven2"/>
		</yank>

and it will pull new artifacts as needed.
