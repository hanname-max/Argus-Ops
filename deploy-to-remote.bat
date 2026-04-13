@echo off
chcp 65001 >nul
echo ========================================
echo       AIOps-Engine 远程部署工具
echo ========================================
echo.
echo 远程配置信息：
echo   - 目标端口：20/tcp (HTTP)
echo   - 需要远程服务器已开放20端口且无占用
echo   - 需要远程Linux服务器已安装JDK 21
echo.

set /p SERVER_IP=请输入远程服务器IP地址:

echo.
echo 确认配置信息：
echo   远程服务器IP: %SERVER_IP%
echo   目标端口: 20
echo   JAR包将上传到: ~/aiops/
echo.

set /p CONFIRM=确认是否开始部署？ (y/N):
if /i not "%CONFIRM%"=="y" goto :cancel

echo.
echo 正在编译项目...
call mvn clean package -DskipTests
if errorlevel 1 goto :build_fail

echo.
echo 编译完成，准备上传...
echo.

if not exist "aiops-bootstrap\target\aiops-bootstrap-1.0.0-SNAPSHOT.jar" (
    echo [错误] 未找到编译产物 JAR 文件！
    goto :end
)

echo JAR文件位置：aiops-bootstrap\target\aiops-bootstrap-1.0.0-SNAPSHOT.jar
echo.
echo 请执行以下上传命令（需要scp，使用Git Bash或WSL执行更佳）：
echo --------------------------------------------------------
echo mkdir -p ~/aiops
echo scp aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar root@%SERVER_IP%:~/aiops/
echo.
echo 上传完成后，在远程服务器执行：
echo --------------------------------------------------------
echo cd ~/aiops
echo.
echo # 检查端口是否被占用
echo lsof -i:20 || echo "端口20未被占用，可以启动"
echo.
echo # 启动应用（使用环境变量传入API Key）
echo export ANTHROPIC_API_KEY=6bb65db7-422e-4e30-93f1-b17407cd35a7
echo java -Dserver.port=20 -jar aiops-bootstrap-1.0.0-SNAPSHOT.jar
echo.
echo # 如需后台运行：
echo nohup java -Dserver.port=20 -jar aiops-bootstrap-1.0.0-SNAPSHOT.jar > app.log 2>&1 &
echo.

echo.
echo [提示] 复制上述命令到远程服务器执行即可完成部署
echo [提示] 部署完成后访问：http://%SERVER_IP%/ 测试服务

goto :end

:build_fail
echo.
echo [错误] 编译失败，请检查错误信息重试
goto :end

:cancel
echo.
echo 已取消部署
goto :end

:end
pause
