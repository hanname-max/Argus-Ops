# Argus-Ops 🚀

> 先解决一个最现实的痛点：很多大学生不是不会用项目，而是先被 `JDK`、`Maven`、打包流程、环境变量这些部署步骤劝退了 😵
>
> 现在不用先硬着头皮自己编译了，仓库里已经直接放好了可运行的 `jar` 包。拉下代码后，找到包、执行一条命令，就能把服务跑起来 ✨

## 🎯 这次主打什么

- ✅ 把可直接运行的后端 `jar` 提交进仓库
- ✅ 用户不需要先执行 `mvn package`
- ✅ 只要本机装了 `JDK 21`，就可以直接启动
- ✅ 想先体验接口能力，下载包就能开跑

## 📦 下载即跑

可直接运行的包在这里：

```text
release/aiops-bootstrap-2.0.0-SNAPSHOT.jar
```

### 1. 准备环境

- `JDK 21`

### 2. 一条命令启动

```powershell
java -jar .\release\aiops-bootstrap-2.0.0-SNAPSHOT.jar
```

### 3. 默认访问地址

- 服务地址：`http://127.0.0.1:8080`
- API 前缀：`http://127.0.0.1:8080/api/v1/ops`

## 🧪 30 秒自检

启动后可以先看端口是否起来：

```powershell
Test-NetConnection -ComputerName 127.0.0.1 -Port 8080
```

如果看到 `TcpTestSucceeded : True`，说明服务已经成功启动 🎉

## 🖥️ 如果要和 AiOpsWeb 一起跑

后端先启动，然后在 `AiOpsWeb` 目录执行：

```powershell
npm start
```

再打开：

```text
http://127.0.0.1:3001
```

`AiOpsWeb` 会把 `/api` 请求代理到 `127.0.0.1:8080`，所以后端先起来，前端体验会更顺滑。

## 🤖 AI 环境变量是可选项

如果你暂时只是想把服务跑起来，不配 AI 相关环境变量也能启动；只是部分 AI 增强能力会降级或跳过。

```powershell
$env:LOCAL_AI_KEY="your-local-key"
$env:LOCAL_AI_BASE_URL="https://api.openai.com/v1"
$env:LOCAL_AI_MODEL="gpt-4o-mini"

$env:REMOTE_AI_KEY="your-remote-key"
$env:REMOTE_AI_BASE_URL="https://api.openai.com/v1"
$env:REMOTE_AI_MODEL="gpt-4o-mini"
```

## 🛠️ 想自己重新打包也可以

如果你后面要自己改代码，再重新构建：

```powershell
mvn -DskipTests clean package
```

重新生成的启动包默认在：

```text
aiops-bootstrap/target/aiops-bootstrap-2.0.0-SNAPSHOT.jar
```

## 🌟 项目能力一眼看懂

- 🧱 生成构建脚本或 Docker 部署脚本
- 🔍 检查目标项目运行环境
- 🪵 过滤和提炼错误日志
- 🛰️ 通过 SSH 执行远程部署命令
- 🤝 本地优先，远程 AI 辅助分析

## 📌 给第一次上手的同学一句话总结

别先和 Maven 较劲，也别先研究一堆配置。

先下载仓库里的 `jar`，跑起来、看到接口通，再慢慢看源码，这样最省时间，也最不容易被部署流程劝退 💡
