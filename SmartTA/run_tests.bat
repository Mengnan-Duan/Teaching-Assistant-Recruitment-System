@echo off
setlocal enabledelayedexpansion
set ROOT=D:\Tomcat\apache-tomcat-10.1.48\webapps\SmartTA
set TOMCAT=D:\Tomcat\apache-tomcat-10.1.48
set SRC=%ROOT%\src\main\java
set TEST_SRC=%ROOT%\src\test\java
set OUT=%ROOT%\WEB-INF\classes
set TEST_OUT=%ROOT%\target\test-classes

REM ── 生产代码编译 classpath ──
set PROD_CP=%TOMCAT%\lib\servlet-api.jar;^
%TOMCAT%\lib\jackson-core-2.17.3.jar;^
%TOMCAT%\lib\jackson-databind-2.17.3.jar;^
%TOMCAT%\lib\jackson-datatype-jsr310-2.17.3.jar;^
%TOMCAT%\lib\jackson-annotations-2.17.3.jar

REM ── 测试代码编译 classpath ──
set TEST_COMPILE_CP=%TOMCAT%\lib\servlet-api.jar;^
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
%TOMCAT%\bin\byte-buddy-agent.jar;^
%OUT%

REM ── 测试运行 classpath ──
set TEST_RUN_CP=%TOMCAT%\lib\servlet-api.jar;^
%TOMCAT%\lib\jackson-core-2.17.3.jar;^
%TOMCAT%\lib\jackson-databind-2.17.3.jar;^
%TOMCAT%\lib\jackson-datatype-jsr310-2.17.3.jar;^
%TOMCAT%\lib\jackson-annotations-2.17.3.jar;^
%TOMCAT%\bin\junit-runner.jar;^
%TOMCAT%\bin\junit-jupiter-api.jar;^
%TOMCAT%\bin\apiguardian-api.jar;^
%TOMCAT%\bin\opentest4j.jar;^
%TOMCAT%\bin\assertj-core.jar;^
%TOMCAT%\bin\mockito-core.jar;^
%TOMCAT%\bin\byte-buddy.jar;^
%TOMCAT%\bin\byte-buddy-agent.jar;^
%OUT%;^
%TEST_OUT%

echo ====================================================
echo   SmartTA 测试套件  (v3.0)
echo ====================================================
echo.

REM ── Step 1: 编译生产代码 ──
echo [1/3] 编译生产代码...
cd /d "%SRC%"
javac -encoding UTF-8 -cp "%PROD_CP%" -d "%OUT%" ^
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
    echo [FAIL] 生产代码编译失败
    exit /b 1
)
echo [OK] 生产代码编译成功

REM ── Step 2: 编译测试代码 ──
echo.
echo [2/3] 编译测试代码...
if not exist "%TEST_OUT%" mkdir "%TEST_OUT%"
cd /d "%TEST_SRC%"
javac -encoding UTF-8 -cp "%TEST_COMPILE_CP%" -d "%TEST_OUT%" ^
  com\bupt\smartta\model\ApplicationTest.java ^
  com\bupt\smartta\model\PositionTest.java ^
  com\bupt\smartta\model\TAPplicantTest.java ^
  com\bupt\smartta\model\MoTaMessageTest.java ^
  com\bupt\smartta\model\UserTest.java ^
  com\bupt\smartta\model\SystemLogTest.java ^
  com\bupt\smartta\model\SystemConfigTest.java ^
  com\bupt\smartta\util\DataStoreTest.java ^
  com\bupt\smartta\servlet\ApiServletTest.java
if errorlevel 1 (
    echo [FAIL] 测试代码编译失败
    exit /b 1
)
echo [OK] 测试代码编译成功

REM ── Step 3: 运行 JUnit ──
echo.
echo [3/3] 运行 JUnit 测试...
cd /d "%TEST_OUT%"
"%JAVA_HOME%\bin\java.exe" -cp "%TEST_RUN_CP%" ^
  org.junit.platform.console.ConsoleLauncher ^
  --classpath "%TEST_OUT%" ^
  --scan-classpath
if errorlevel 1 (
    echo.
    echo [WARN] 部分测试失败
) else (
    echo.
    echo [OK] 全部测试通过
)
exit /b 0
