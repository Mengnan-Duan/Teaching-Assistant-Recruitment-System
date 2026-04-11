@echo off
setlocal enabledelayedexpansion
set ROOT=D:\Tomcat\apache-tomcat-10.1.48\webapps\SmartTA
set SRC=%ROOT%\src\main\java
set OUT=%ROOT%\WEB-INF\classes
set TOMCAT=D:\Tomcat\apache-tomcat-10.1.48

REM ── 生产代码 classpath ──
REM Jackson 2.17.x 位于 Tomcat lib；测试依赖（junit-runner 等）位于 Tomcat bin
set CP=%TOMCAT%\lib\servlet-api.jar;^
%TOMCAT%\lib\jackson-core-2.17.3.jar;^
%TOMCAT%\lib\jackson-databind-2.17.3.jar;^
%TOMCAT%\lib\jackson-datatype-jsr310-2.17.3.jar;^
%TOMCAT%\lib\jackson-annotations-2.17.3.jar;^
%TOMCAT%\bin\junit-jupiter-api.jar;^
%TOMCAT%\bin\junit-runner.jar;^
%TOMCAT%\bin\apiguardian-api.jar;^
%TOMCAT%\bin\opentest4j.jar;^
%TOMCAT%\bin\assertj-core.jar;^
%TOMCAT%\bin\mockito-core.jar;^
%TOMCAT%\bin\byte-buddy.jar;^
%TOMCAT%\bin\byte-buddy-agent.jar

echo ====================================================
echo   SmartTA v3.0 编译脚本
echo ====================================================
echo CP=%CP%
cd /d "%SRC%"
javac -encoding UTF-8 -cp "%CP%" -d "%OUT%" ^
  com\bupt\smartta\model\SystemConfig.java ^
  com\bupt\smartta\model\SystemLog.java ^
  com\bupt\smartta\model\Application.java ^
  com\bupt\smartta\model\Position.java ^
  com\bupt\smartta\model\TAPplicant.java ^
  com\bupt\smartta\model\MoTaMessage.java ^
  com\bupt\smartta\model\User.java ^
  com\bupt\smartta\util\JsonFileStore.java ^
  com\bupt\smartta\util\LLMService.java ^
  com\bupt\smartta\util\DataStore.java ^
  com\bupt\smartta\servlet\AuthServlet.java ^
  com\bupt\smartta\servlet\ApiServlet.java ^
  com\bupt\smartta\servlet\UploadServlet.java ^
  com\bupt\smartta\servlet\DownloadServlet.java ^
  com\bupt\smartta\filter\SecurityHeadersFilter.java ^
  com\bupt\smartta\listener\AppContextListener.java
if errorlevel 1 (
  echo.
  echo [FAIL] BUILD FAILED
  exit /b 1
)
echo.
echo [OK] BUILD SUCCESS — 请重启 Tomcat 以加载新类
exit /b 0
