@echo off
rem Copyright 1999-2018 Alibaba Group Holding Ltd.
rem Licensed under the Apache License, Version 2.0 (the "License");
rem you may not use this file except in compliance with the License.
rem You may obtain a copy of the License at
rem
rem      http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.
if not exist "%JAVA_HOME%\bin\java.exe" echo Please set the JAVA_HOME variable in your environment, We need java(x64)! jdk8 or later is better! & EXIT /B 1
set "JAVA=%JAVA_HOME%\bin\java.exe"

setlocal

set BASE_DIR=%~dp0
rem added double quotation marks to avoid the issue caused by the folder names containing spaces.
rem removed the last 5 chars(which means \bin\) to get the base DIR.
set BASE_DIR="%BASE_DIR:~0,-5%"

set DEFAULT_SEARCH_LOCATIONS="classpath:/,classpath:/config/,file:./,file:./config/"
set CUSTOM_SEARCH_LOCATIONS=%DEFAULT_SEARCH_LOCATIONS%,file:%BASE_DIR%/conf/



if not "%2" == "cluster" (
    set "JAVA_OPT=%JAVA_OPT% -Xms512m -Xmx512m -Xmn256m"
    set "JAVA_OPT=%JAVA_OPT% -Dnacos.standalone=true"
 ) else (
    set "JAVA_OPT=%JAVA_OPT% -server -Xms2g -Xmx2g -Xmn1g -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=320m"
    set "JAVA_OPT=%JAVA_OPT% -XX:-OmitStackTraceInFastThrow XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=%BASE_DIR%\logs\java_heapdump.hprof"
    set "JAVA_OPT=%JAVA_OPT% -XX:-UseLargePages"
 )

set "JAVA_OPT=%JAVA_OPT% -Xbootclasspath/a:%BASE_DIR%\plugins\cmdb"
set "JAVA_OPT=%JAVA_OPT% -Dnacos.home=%BASE_DIR%"
set "JAVA_OPT=%JAVA_OPT% -jar %BASE_DIR%\target\nacos-server.jar"
set "JAVA_OPT=%JAVA_OPT% --spring.config.location=%CUSTOM_SEARCH_LOCATIONS%"
set "JAVA_OPT=%JAVA_OPT% --logging.config=%BASE_DIR%/conf/nacos-logback.xml"

call "%JAVA%" %JAVA_OPT% nacos.nacos %*
