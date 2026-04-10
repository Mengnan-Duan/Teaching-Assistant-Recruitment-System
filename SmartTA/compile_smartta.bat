@echo off
setlocal enabledelayedexpansion
set ROOT=D:\Tomcat\apache-tomcat-10.1.48\webapps\SmartTA
set SRC=%ROOT%\src\main\java
set OUT=%ROOT%\WEB-INF\classes
set TOMCAT=D:\Tomcat\apache-tomcat-10.1.48
REM Jackson 2.17.x 与 Tomcat lib 中版本一致；缺少 jackson-annotations 会导致运行时 NoClassDefFoundError: JsonView
set CP=%TOMCAT%\lib\servlet-api.jar;%TOMCAT%\lib\jackson-core-2.17.3.jar;%TOMCAT%\lib\jackson-databind-2.17.3.jar;%TOMCAT%\lib\jackson-datatype-jsr310-2.17.3.jar;%ROOT%\WEB-INF\lib\jackson-annotations-2.17.3.jar
echo Compiling SmartTA (javac -encoding UTF-8)...
echo CP=%CP%
cd /d "%SRC%"
javac -encoding UTF-8 -cp "%CP%" -d "%OUT%" ^
  com\bupt\smartta\model\SystemConfig.java ^
  com\bupt\smartta\model\SystemLog.java ^
  com\bupt\smartta\model\Application.java ^
  com\bupt\smartta\model\Position.java ^
  com\bupt\smartta\model\TAPplicant.java ^
  com\bupt\smartta\model\User.java ^
  com\bupt\smartta\util\JsonFileStore.java ^
  com\bupt\smartta\util\DataStore.java ^
  com\bupt\smartta\servlet\AuthServlet.java ^
  com\bupt\smartta\servlet\ApiServlet.java ^
  com\bupt\smartta\servlet\UploadServlet.java ^
  com\bupt\smartta\filter\SecurityHeadersFilter.java ^
  com\bupt\smartta\listener\AppContextListener.java
if errorlevel 1 (
  echo BUILD FAILED
  exit /b 1
)
echo Done! Restart Tomcat to load new classes.
exit /b 0
