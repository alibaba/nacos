Build Instructions for NACOS

====================================================

(1) Prerequisites

    JDK 1.8+ is required in order to compile and run Nacos.

    nacos utilizes Maven as a distribution management and packaging tool. Version 3.0.3 or later is required.
    Maven installation and configuration instructions can be found here:

    http://maven.apache.org/run-maven/index.html


(2) Run test cases

    Execute the following command in order to compile and run test cases of each components:

    $ mvn test


(3) Import projects to Eclipse IDE

    First, generate eclipse project files:

    $ mvn -U eclipse:eclipse

    Then, import to eclipse by specifying the root directory of the project via:

    [File] > [Import] > [Existing Projects into Workspace].


(4) Build distribution packages

    Execute the following command in order to build the tar.gz packages and install JAR into local repository:

	#build nacos
    $ mvn -Prelease-nacos -Dmaven.test.skip=true clean install -U
