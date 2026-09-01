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
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
@rem ##########################################################################
@rem  Temporaires du build isoles dans le projet (US-16, corrige US-17)
@rem
@rem  sqlite-jdbc extrait sa DLL native au premier acces, dans org.sqlite.tmpdir a defaut
@rem  java.io.tmpdir. Ces deux proprietes doivent valoir pour TOUTES les JVM du build, pas
@rem  seulement pour le lanceur : la tache SQLDelight verifyCommonMainLedgerHubDatabaseMigration
@rem  s'execute dans un worker Gradle, un processus distinct qui ne voit ni les -D passes ici au
@rem  lanceur, ni les System.setProperty du script de build. Sans TMP/TEMP, ce worker retombait
@rem  sur C:\WINDOWS -- non inscriptible hors administrateur, d'ou
@rem  AccessDeniedException: C:\WINDOWS\sqlite-...-sqlitejdbc.dll.lck.
@rem
@rem  TMP/TEMP et JAVA_TOOL_OPTIONS, eux, sont herites par toute JVM enfant : lanceur, demon,
@rem  workers, JVM de test. Le chemin est derive de %APP_HOME%, donc portable d'un poste a
@rem  l'autre et supprime par `clean`. Nos -D sont AJOUTES a la fin d'un JAVA_TOOL_OPTIONS
@rem  existant : a proprietes egales la derniere gagne, sans perdre les options du poste.
@rem ##########################################################################
set LEDGERHUB_JVM_TMP=%APP_HOME%\build\tmp\jvm
set LEDGERHUB_SQLITE_TMP=%APP_HOME%\build\tmp\sqlite
if not exist "%LEDGERHUB_JVM_TMP%" mkdir "%LEDGERHUB_JVM_TMP%" 2>NUL
if not exist "%LEDGERHUB_SQLITE_TMP%" mkdir "%LEDGERHUB_SQLITE_TMP%" 2>NUL
set TMP=%LEDGERHUB_JVM_TMP%
set TEMP=%LEDGERHUB_JVM_TMP%
set JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% "-Djava.io.tmpdir=%LEDGERHUB_JVM_TMP%" "-Dorg.sqlite.tmpdir=%LEDGERHUB_SQLITE_TMP%"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
