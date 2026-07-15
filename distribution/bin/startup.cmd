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

setlocal enabledelayedexpansion

set BASE_DIR=%~dp0
rem added double quotation marks to avoid the issue caused by the folder names containing spaces.
rem removed the last 5 chars(which means \bin\) to get the base DIR.
set BASE_DIR="%BASE_DIR:~0,-5%"

set CUSTOM_SEARCH_LOCATIONS=file:%BASE_DIR%/conf/

set MODE="cluster"
set FUNCTION_MODE="all"
set SERVER=nacos-server
set MODE_INDEX=-1
set FUNCTION_MODE_INDEX=-1
set SERVER_INDEX=-1
set EMBEDDED_STORAGE_INDEX=-1
set EMBEDDED_STORAGE=""
set DEPLOYMENT_INDEX=-1
set DEPLOYMENT="merged"

set i=0
for %%a in (%*) do (
    if "%%a" == "-m" ( set /a MODE_INDEX=!i!+1 )
    if "%%a" == "-f" ( set /a FUNCTION_MODE_INDEX=!i!+1 )
    if "%%a" == "-s" ( set /a SERVER_INDEX=!i!+1 )
    if "%%a" == "-p" ( set /a EMBEDDED_STORAGE_INDEX=!i!+1 )
    if "%%a" == "-d" ( set /a DEPLOYMENT_INDEX=!i!+1 )
    set /a i+=1
)

set i=0
for %%a in (%*) do (
    if %MODE_INDEX% == !i! ( set MODE="%%a" )
    if %FUNCTION_MODE_INDEX% == !i! ( set FUNCTION_MODE="%%a" )
    if %SERVER_INDEX% == !i! (set SERVER="%%a")
    if %EMBEDDED_STORAGE_INDEX% == !i! (set EMBEDDED_STORAGE="%%a")
    if %DEPLOYMENT_INDEX% == !i! (set DEPLOYMENT="%%a")
    set /a i+=1
)

call :Process_compatible_base64_config "nacos.plugin.auth.nacos.token.secret.key" "nacos.core.auth.plugin.nacos.token.secret.key" %BASE_DIR%\conf\application.properties
call :Process_required_config "nacos.core.auth.server.identity.key" %BASE_DIR%\conf\application.properties
call :Process_required_config "nacos.core.auth.server.identity.value" %BASE_DIR%\conf\application.properties

rem if nacos startup mode is standalone
if %MODE% == "standalone" (
    echo "nacos is starting with standalone"
	  set "NACOS_OPTS=-Dnacos.standalone=true"
    if "%CUSTOM_NACOS_MEMORY%"=="" ( set "CUSTOM_NACOS_MEMORY=-Xms512m -Xmx512m -Xmn256m" )
    set "NACOS_JVM_OPTS=%CUSTOM_NACOS_MEMORY%"
)

rem if nacos startup mode is cluster
if %MODE% == "cluster" (
    echo "nacos is starting with cluster"
	  if %EMBEDDED_STORAGE% == "embedded" (
	      set "NACOS_OPTS=-DembeddedStorage=true"
	  )
    if "%CUSTOM_NACOS_MEMORY%"=="" ( set "CUSTOM_NACOS_MEMORY=-Xms2g -Xmx2g -Xmn1g -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=320m" )
    set "NACOS_JVM_OPTS=-server %CUSTOM_NACOS_MEMORY% -XX:-OmitStackTraceInFastThrow -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=%BASE_DIR%\logs\java_heapdump.hprof -XX:-UseLargePages"
)

rem set nacos's functionMode
if %FUNCTION_MODE% == "config" (
    set "NACOS_OPTS=%NACOS_OPTS% -Dnacos.functionMode=config"
)

if %FUNCTION_MODE% == "naming" (
    set "NACOS_OPTS=%NACOS_OPTS% -Dnacos.functionMode=naming"
)

if %FUNCTION_MODE% == "microservice" (
    set "NACOS_OPTS=%NACOS_OPTS% -Dnacos.functionMode=microservice"
)

if %FUNCTION_MODE% == "ai" (
    set "NACOS_OPTS=%NACOS_OPTS% -Dnacos.functionMode=ai"
)

rem set JVM options for Java 9+
for /f tokens^=2-5^ delims^=.-_+^" %%j in ('"%JAVA%" -fullversion 2^>^&1') do set "JAVA_MAJOR_VERSION=%%j"
if %JAVA_MAJOR_VERSION% GEQ 9 (
    set "NACOS_JVM_OPTS=%NACOS_JVM_OPTS% --add-opens=java.base/java.lang=ALL-UNNAMED"
    set "NACOS_JVM_OPTS=%NACOS_JVM_OPTS% --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
    set "NACOS_JVM_OPTS=%NACOS_JVM_OPTS% --add-opens=java.base/java.util=ALL-UNNAMED"
)

rem set nacos options
set "NACOS_OPTS=%NACOS_OPTS% -Dnacos.deployment.type=%DEPLOYMENT%"
set "NACOS_OPTS=%NACOS_OPTS% -Dloader.path=%BASE_DIR%/plugins,%BASE_DIR%/plugins/health,%BASE_DIR%/plugins/cmdb,%BASE_DIR%/plugins/selector"
set "NACOS_OPTS=%NACOS_OPTS% -Dnacos.home=%BASE_DIR%"
set "NACOS_OPTS=%NACOS_OPTS% -jar %BASE_DIR%\target\%SERVER%.jar"

rem set nacos spring config location
set "NACOS_CONFIG_OPTS=--spring.config.additional-location=%CUSTOM_SEARCH_LOCATIONS%"

rem set nacos log4j file location
set "NACOS_LOG4J_OPTS=--logging.config=%BASE_DIR%/conf/nacos-logback.xml"


set COMMAND="%JAVA%" %NACOS_JVM_OPTS% %NACOS_OPTS% %NACOS_CONFIG_OPTS% %NACOS_LOG4J_OPTS% nacos.nacos %*

rem start nacos command
%COMMAND%

pause

goto :EOF

:Process_compatible_base64_config
    setlocal enabledelayedexpansion
    set "canonical_key=%~1"
    set "legacy_key=%~2"
    set "target_file=%~3"
    set "target_file=!target_file:"=!"
    call :Read_config_value "!canonical_key!" "!target_file!" canonical_value
    call :Read_config_value "!legacy_key!" "!target_file!" legacy_value
    if defined canonical_value (
        if defined legacy_value echo Both `!canonical_key!` and legacy `!legacy_key!` are configured; the preferred key wins.
        endlocal
        exit /b
    )
    if defined legacy_value (
        call :Validate_base64 "!legacy_value!"
        if !errorlevel! == 0 (
            call :Write_config_value "!canonical_key!" "!legacy_value!" "!target_file!"
            echo Migrated legacy `!legacy_key!` to preferred `!canonical_key!`.
            endlocal
            exit /b
        )
    )
    echo The initial key used to generate JWT tokens must decode to at least 32 bytes.
    :Prompt_base64_config
    set /p "input_val=!canonical_key! is missing, please set with Base64 string: "
    call :Validate_base64 "!input_val!"
    if not !errorlevel! == 0 (
        echo Invalid Base64 token secret, please input again.
        goto Prompt_base64_config
    )
    call :Write_config_value "!canonical_key!" "!input_val!" "!target_file!"
    echo `!canonical_key!` updated.
    endlocal
    exit /b

:Read_config_value
    setlocal enabledelayedexpansion
    set "result="
    for /f "usebackq delims=" %%a in ("%~2") do (
        for /f "tokens=1 delims==" %%b in ("%%a") do (
            if "%%b"=="%~1" (
                set "line=%%a"
                set "result=!line:*==!"
            )
        )
    )
    endlocal & set "%~3=%result%"
    exit /b

:Validate_base64
    setlocal
    set "NACOS_SECRET_TO_VALIDATE=%~1"
    powershell -NoProfile -NonInteractive -Command "try {$v=[Convert]::FromBase64String($env:NACOS_SECRET_TO_VALIDATE); if ($v.Length -lt 32) {exit 1}} catch {exit 1}" >nul 2>&1
    set "validation_result=%errorlevel%"
    endlocal & exit /b %validation_result%

:Write_config_value
    setlocal enabledelayedexpansion
    set "key_pattern=%~1"
    set "input_val=%~2"
    set "target_file=%~3"
    set "temp_file=%TEMP%\temp_%RANDOM%.tmp"
    set "key_pattern_with_equal=!key_pattern!="
    set "updated=false"
    for /f "usebackq delims=" %%a in ("!target_file!") do (
        set "line=%%a"
        if "!line!"=="!key_pattern_with_equal!" (
            echo %%a!input_val!>>"!temp_file!"
            set "updated=true"
        ) else (
            echo %%a>>"!temp_file!"
        )
    )
    if "!updated!"=="false" echo !key_pattern!=!input_val!>>"!temp_file!"
    move /Y "!temp_file!" "!target_file!" >nul
    endlocal
    exit /b

:Process_required_config
    setlocal
    set "key_pattern=%~1"
    set "target_file=%~2"
    set "target_file=!target_file:"=!"

    set "escaped_key=%key_pattern:.=\.%"

    findstr /R /C:"^%escaped_key%[= ].*" "%target_file%" >nul
    if %errorlevel% == 0 (
        rem Check if the value of the key is empty
        for /f "usebackq tokens=1,2 delims==" %%a in ("%target_file%") do (
            if "%%a"=="%key_pattern%" if "%%b"=="" (
                rem Value is empty, request input from user
                set /p "input_val=%key_pattern% value is empty, please input: "
                set "temp_file=%TEMP%\temp_%RANDOM%.tmp"
                set "key_pattern_with_equal=!key_pattern!="

                for /f "usebackq delims=" %%a in ("!target_file!") do (
                    set "line=%%a"
                    set "line=!line: =!"
                    if "!line!"=="!key_pattern_with_equal!" (
                        echo %%a!input_val!>>"!temp_file!"
                    ) else (
                        echo %%a>>"!temp_file!"
                    )
                )
                move /Y "!temp_file!" "!target_file!" >nul
                echo %key_pattern% Updated with new value:%input_val%
                findstr /R "^%escaped_key%" "%target_file%"
                echo ----------------------------------
                exit /b
            )
        )
    )
    endlocal
