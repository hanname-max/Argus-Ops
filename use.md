# Argus-Ops 使用说明

## 1. 项目简介

Argus-Ops 是一个基于 Spring Boot 的本地优先运维辅助工具，主要能力包括：

- 扫描目标项目目录
- 生成构建脚本或 Docker 部署脚本
- 检查项目运行环境
- 过滤和分析错误日志
- 通过 SSH 执行远程部署命令
- 在配置 AI 时提供本地分析增强和远程辅助

当前 API 基础路径为：

```text
http://127.0.0.1:8080/api/v1/ops
```

## 2. 模块结构

项目是一个 Maven 多模块工程：

- `aiops-type`：基础类型、异常、通用链路模型
- `aiops-application`：用例层、应用服务、DTO
- `aiops-infrastructure`：AI 适配器、SSH 适配器、配置装配
- `aiops-web`：HTTP 接口层
- `aiops-bootstrap`：Spring Boot 启动模块

## 3. 运行前准备

### 3.1 环境要求

- JDK 21
- Maven 3.9+

### 3.2 可选环境变量

如果不配置 AI Key，项目仍可运行，但会走本地优先的降级逻辑。

```powershell
$env:LOCAL_AI_KEY="your-local-key"
$env:LOCAL_AI_BASE_URL="https://api.openai.com/v1"
$env:LOCAL_AI_MODEL="gpt-4o-mini"

$env:REMOTE_AI_KEY="your-remote-key"
$env:REMOTE_AI_BASE_URL="https://api.openai.com/v1"
$env:REMOTE_AI_MODEL="gpt-4o-mini"
```

说明：

- `LOCAL_AI_*` 用于本地分析增强
- `REMOTE_AI_*` 用于远程辅助分析
- 未配置或使用占位值时，系统会跳过对应 AI 能力

## 4. 构建与启动

### 4.1 构建项目

在项目根目录执行：

```powershell
mvn -DskipTests package
```

### 4.2 启动服务

使用可执行 Jar 启动：

```powershell
java -jar aiops-bootstrap/target/aiops-bootstrap-2.0.0-SNAPSHOT.jar
```

默认端口：

```text
8080
```

启动成功后访问：

```text
http://127.0.0.1:8080
```

## 5. 接口使用示例

### 5.1 生成构建脚本

接口：

```text
POST /api/v1/ops/build-script
```

请求示例：

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/ops/build-script" \
  -H "Content-Type: application/json" \
  -d "{\"projectPath\":\"D:/workspace/demo\",\"targetType\":\"linux-docker\"}"
```

请求字段：

- `projectPath`：目标项目路径
- `targetType`：目标类型，例如 `linux-docker`

典型用途：

- 为 Java 项目生成构建命令
- 在没有 Dockerfile 时生成基础 Docker 构建脚本

### 5.2 环境检查

接口：

```text
POST /api/v1/ops/environment/check
```

请求示例：

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/ops/environment/check" \
  -H "Content-Type: application/json" \
  -d "{\"projectPath\":\"D:/workspace/demo\"}"
```

请求字段：

- `projectPath`：待检查项目路径

检查内容通常包括：

- 构建工具识别
- 运行时识别
- Dockerfile 检查
- 关键配置文件检查
- 可选 AI 风险补充

### 5.3 日志过滤与分析

接口：

```text
POST /api/v1/ops/logs/filter
```

请求示例：

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/ops/logs/filter" \
  -H "Content-Type: application/json" \
  -d "{\"rawLog\":\"java.net.ConnectException: Connection refused\"}"
```

请求字段：

- `rawLog`：原始日志文本

典型输出：

- 关键错误行
- 简化后的问题摘要
- 针对错误的处理建议

### 5.4 远程命令部署

接口：

```text
POST /api/v1/ops/deploy/exec
```

请求示例：

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/ops/deploy/exec" \
  -H "Content-Type: application/json" \
  -d "{\"host\":\"192.168.1.10\",\"port\":22,\"username\":\"root\",\"password\":\"123456\",\"command\":\"bash /opt/deploy.sh\"}"
```

请求字段：

- `host`：远程主机地址
- `port`：SSH 端口
- `username`：用户名
- `password`：密码
- `command`：要执行的远程命令

注意：

- 该接口依赖目标机器可达
- 需要有效 SSH 账号和密码
- 下游 SSH 执行失败时通常返回 `502`

## 6. 错误处理

当前项目已实现统一异常映射，常见行为如下：

- 非法 JSON：返回 `400`
- 请求字段不合法：返回 `400`
- AI 或远程调用失败：返回 `502`

常见错误码示例：

```json
{
  "code": "AIOPS_400",
  "message": "projectPath is required"
}
```

## 7. 当前项目的建议使用方式

建议按下面顺序使用：

1. 先调用 `/environment/check` 判断目标项目环境是否完整
2. 再调用 `/build-script` 生成构建或 Docker 脚本
3. 部署失败后调用 `/logs/filter` 分析日志
4. 需要实际远程执行时，再调用 `/deploy/exec`

## 8. SESSION_CONTEXT.md 的作用

当前仓库根目录下的 `SESSION_CONTEXT.md` 不是运行时代码配置文件，也不是 Spring Boot 自动加载文件。

它的作用更偏向于：

- 记录当前项目的已验证状态
- 记录关键改动点
- 记录可用接口和验证结果
- 作为协作时的会话上下文说明

也就是说：

- 应用启动不依赖它
- Java 代码当前没有直接读取它
- 它更适合给开发者、协作者或 AI 代理快速了解项目现状

## 9. 常用命令汇总

```powershell
# 构建
mvn -DskipTests package

# 启动
java -jar aiops-bootstrap/target/aiops-bootstrap-2.0.0-SNAPSHOT.jar

# 查看根目录说明
Get-Content .\use.md
Get-Content .\SESSION_CONTEXT.md
```

## 10. 后续建议

如果你希望这个项目更适合团队使用，下一步建议补充：

- 更完整的 `README.md`
- 接口返回示例
- `.env.example` 或环境变量说明文件
- Postman 集合或 `curl` 脚本
- 部署日志持久化和 SSE 流式输出能力
