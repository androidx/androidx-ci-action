@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  cli startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and CLI_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\cli.jar;%APP_HOME%\lib\lib-1.0.5753569798.jar;%APP_HOME%\lib\log4j-api-kotlin-1.0.0.jar;%APP_HOME%\lib\converter-moshi-2.9.0.jar;%APP_HOME%\lib\retrofit-2.9.0.jar;%APP_HOME%\lib\logging-interceptor-4.9.1.jar;%APP_HOME%\lib\okhttp-4.9.1.jar;%APP_HOME%\lib\kotlin-reflect-1.3.0.jar;%APP_HOME%\lib\moshi-adapters-1.14.0.jar;%APP_HOME%\lib\moshi-metadata-reflect-0.21.0.jar;%APP_HOME%\lib\kotlinx-coroutines-core-jvm-1.6.1.jar;%APP_HOME%\lib\clikt-jvm-3.2.0.jar;%APP_HOME%\lib\moshi-1.14.0.jar;%APP_HOME%\lib\kotlin-stdlib-jdk8-1.8.0.jar;%APP_HOME%\lib\kotlinx-metadata-jvm-0.5.0.jar;%APP_HOME%\lib\kotlin-stdlib-jdk7-1.8.0.jar;%APP_HOME%\lib\okio-jvm-2.10.0.jar;%APP_HOME%\lib\kotlin-stdlib-1.8.0.jar;%APP_HOME%\lib\log4j-core-2.17.2.jar;%APP_HOME%\lib\google-cloud-storage-2.6.0.jar;%APP_HOME%\lib\google-cloud-datastore-2.4.0.jar;%APP_HOME%\lib\kotlin-stdlib-common-1.8.0.jar;%APP_HOME%\lib\annotations-13.0.jar;%APP_HOME%\lib\log4j-api-2.17.2.jar;%APP_HOME%\lib\guava-31.1-jre.jar;%APP_HOME%\lib\failureaccess-1.0.1.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\checker-qual-3.21.4.jar;%APP_HOME%\lib\j2objc-annotations-1.3.jar;%APP_HOME%\lib\google-http-client-1.41.7.jar;%APP_HOME%\lib\opencensus-contrib-http-util-0.31.0.jar;%APP_HOME%\lib\google-http-client-jackson2-1.41.7.jar;%APP_HOME%\lib\google-api-client-1.34.0.jar;%APP_HOME%\lib\google-oauth-client-1.33.3.jar;%APP_HOME%\lib\google-http-client-gson-1.41.7.jar;%APP_HOME%\lib\google-http-client-apache-v2-1.41.7.jar;%APP_HOME%\lib\google-api-services-storage-v1-rev20220210-1.32.1.jar;%APP_HOME%\lib\gson-2.9.0.jar;%APP_HOME%\lib\google-cloud-core-2.6.0.jar;%APP_HOME%\lib\auto-value-annotations-1.9.jar;%APP_HOME%\lib\gax-2.16.0.jar;%APP_HOME%\lib\proto-google-common-protos-2.8.3.jar;%APP_HOME%\lib\google-cloud-core-http-2.6.0.jar;%APP_HOME%\lib\google-http-client-appengine-1.41.7.jar;%APP_HOME%\lib\gax-httpjson-0.101.0.jar;%APP_HOME%\lib\google-auth-library-credentials-1.6.0.jar;%APP_HOME%\lib\google-auth-library-oauth2-http-1.6.0.jar;%APP_HOME%\lib\api-common-2.1.5.jar;%APP_HOME%\lib\javax.annotation-api-1.3.2.jar;%APP_HOME%\lib\opencensus-api-0.31.0.jar;%APP_HOME%\lib\grpc-context-1.45.1.jar;%APP_HOME%\lib\proto-google-iam-v1-1.3.1.jar;%APP_HOME%\lib\protobuf-java-3.19.4.jar;%APP_HOME%\lib\protobuf-java-util-3.19.4.jar;%APP_HOME%\lib\threetenbp-1.6.0.jar;%APP_HOME%\lib\jackson-core-2.13.2.jar;%APP_HOME%\lib\proto-google-cloud-datastore-v1-0.95.0.jar;%APP_HOME%\lib\datastore-v1-proto-client-2.4.0.jar;%APP_HOME%\lib\google-http-client-protobuf-1.41.7.jar;%APP_HOME%\lib\grpc-api-1.45.1.jar;%APP_HOME%\lib\error_prone_annotations-2.13.0.jar;%APP_HOME%\lib\httpclient-4.5.13.jar;%APP_HOME%\lib\commons-logging-1.2.jar;%APP_HOME%\lib\commons-codec-1.15.jar;%APP_HOME%\lib\httpcore-4.4.15.jar


@rem Execute cli
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %CLI_OPTS%  -classpath "%CLASSPATH%" dev.androidx.ci.cli.MainKt %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable CLI_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%CLI_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
