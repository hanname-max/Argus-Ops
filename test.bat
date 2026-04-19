
@echo off
setlocal enabledelayedexpansion

REM ==========================
REM 第一步：设置临时环境变量
REM ==========================
echo [INFO] 设置临时环境变量...
set LOCAL_AI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
set LOCAL_AI_KEY=6e61acd2-be78-4eea-92a3-237a7b370943
set LOCAL_AI_MODEL=glm-4-7-251222

set REMOTE_AI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
set REMOTE_AI_KEY=6e61acd2-be78-4eea-92a3-237a7b370943
set REMOTE_AI_MODEL=glm-4-7-251222

set OPENAI_API_KEY=6e61acd2-be78-4eea-92a3-237a7b370943

REM ==========================
REM 第二步：编译
REM ==========================
echo [INFO] 开始编译项目...
call mvn -DskipTests clean package
if errorlevel 1 (
    echo [ERROR] 编译失败
    exit /b 1
)

REM ==========================
REM 第三步：后台启动服务（Windows 下没有后台启动，这里注释掉，你可以手动启动）
REM ==========================
echo [INFO] 请手动在另一个终端执行：
echo   mvn -f aiops-bootstrap/pom.xml spring-boot:run
echo.
pause

REM ==========================
REM 第四步：测试前三个接口（AI 接口）
REM ==========================
echo.
echo [INFO] 测试接口 1：环境检查...
curl -X POST "http://localhost:8080/api/v1/ops/environment/check" ^
  -H "Content-Type: application/json" ^
  -d "{\"projectPath\":\"%cd:\=/%\"}"

echo.
echo [INFO] 测试接口 2：日志筛选...
curl -X POST "http://localhost:8080/api/v1/ops/logs/filter" ^
  -H "Content-Type: application/json" ^
  -d "{\"rawLog\":\"java.net.ConnectException: Connection refused at deploy step\"}"

echo.
echo [INFO] 测试接口 3：构建脚本生成...
curl -X POST "http://localhost:8080/api/v1/ops/build-script" ^
  -H "Content-Type: application/json" ^
  -d "{\"projectPath\":\"%cd:\=/%\",\"targetType\":\"linux-docker\"}"

echo.
echo [INFO] 前三个 AI 接口测试完成，请检查输出是否正常。
pause

REM ==========================
REM 第五步：测试远程部署（真实 SSH）
REM ==========================
echo.
echo [INFO] 测试接口 4：远程部署（SSH 到 39.106.139.246）...
curl -X POST "http://localhost:8080/api/v1/ops/deploy/exec" ^
  -H "Content-Type: application/json" ^
  -d "{\"host\":\"39.106.139.246\",\"port\":22,\"username\":\"root\",\"password\":\"qqww235689\",\"command\":\"echo hello && uname -a\"}"

echo.
echo [INFO] 所有测试完成。
endlocal

