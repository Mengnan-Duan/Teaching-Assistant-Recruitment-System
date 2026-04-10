$cp = "D:\Tomcat\apache-tomcat-10.1.48\lib\servlet-api.jar;D:\Tomcat\apache-tomcat-10.1.48\lib\jackson-annotations-2.18.2.jar;D:\Tomcat\apache-tomcat-10.1.48\lib\jackson-core-2.18.2.jar;D:\Tomcat\apache-tomcat-10.1.48\lib\jackson-databind-2.18.2.jar;D:\Tomcat\apache-tomcat-10.1.48\lib\commons-text-1.13.1.jar;D:\Tomcat\apache-tomcat-10.1.48\lib\commons-lang3-3.20.0.jar"
$src = "D:\Tomcat\apache-tomcat-10.1.48\webapps\SmartTA\WEB-INF\classes\com\bupt\smartta"
$dest = "D:\Tomcat\apache-tomcat-10.1.48\webapps\SmartTA\WEB-INF\classes"
$files = @(
    "$src\model\SystemConfig.java",
    "$src\model\Position.java",
    "$src\model\TAPplicant.java",
    "$src\model\Application.java",
    "$src\model\SystemLog.java",
    "$src\model\User.java",
    "$src\util\JsonFileStore.java",
    "$src\util\DataStore.java",
    "$src\servlet\ApiServlet.java",
    "$src\servlet\AuthServlet.java",
    "$src\filter\SecurityHeadersFilter.java",
    "$src\listener\AppContextListener.java"
)
Write-Host "Compiling SmartTA..."
$javacCmd = "javac -encoding UTF-8 -cp `"$cp`" -d `"$dest`" " + ($files -join " ")
Write-Host $javacCmd
Invoke-Expression $javacCmd
if ($LASTEXITCODE -eq 0) { Write-Host "All files compiled successfully!" }
else { Write-Host "Compilation failed!" }
