@echo off
rem Copyright 1999-2024 Alibaba Group Holding Ltd.
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

setlocal enabledelayedexpansion

rem nacos mode
set MODE=cluster
set MODE_INDEX=-1

rem read properties
set i=0
for %%a in (%*) do (
    if "%%a" == "-m" ( set /a MODE_INDEX=!i!+1 )
    set /a i+=1
)

set i=0
for %%a in (%*) do (
    if %MODE_INDEX% == !i! ( set MODE="%%a" )
    set /a i+=1
)

rem if nacos startup mode is standalone
if /I %MODE%=="standalone" (
    echo native nacos is starting with standalone
    set "NACOS_OPTS=-Dnacos.standalone=true"
)

rem if nacos startup mode is cluster
if /I %MODE%=="cluster" (
    echo native nacos is starting with cluster
)

rem run nacos native
set COMMAND=D:\Coding\Project_Com\nacos\console\target\nacos-server.exe %NACOS_OPTS% -Dnacos.home=D:\Coding\Project\nacos
%COMMAND%
