@echo off
setlocal enabledelayedexpansion
set ROOT=D:\Tomcat\apache-tomcat-10.1.48\webapps\SmartTA
set SRC=%ROOT%\WEB-INF\classes\com\bupt\smartta
set TOMCAT=D:\Tomcat\apache-tomcat-10.1.48
set CP=%TOMCAT%\lib\servlet-api.jar;%TOMCAT%\lib\jackson-annotations-2.18.2.jar;%TOMCAT%\lib\jackson-core-2.18.2.jar;%TOMCAT%\lib\jackson-databind-2.18.2.jar;%TOMCAT%\lib\commons-text-1.13.1.jar;%TOMCAT%\lib\commons-lang3-3.20.0.jar
echo Compiling SmartTA...
cd /d "%SRC%"
javac -encoding UTF-8 -cp "%CP%" -d "%SRC%" model\SystemConfig.java util\JsonFileStore.java util\DataStore.java servlet\ApiServlet.java servlet\AuthServlet.java filter\SecurityHeadersFilter.java
if errorlevel 1 goto :eof
echo Done!
