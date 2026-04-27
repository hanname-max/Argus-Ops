# Argus-Ops 🚀

Argus-Ops 现在已经升级成 **单 Jar 启动模式** 啦 ✨  

这意味着你不再需要：
- 再单独启动一个前端服务 😵
- 为了打开页面先装 Node.js 🤯
- 开两个终端，一个跑后端，一个跑前端 🥲

现在的目标体验非常直接：

> **下载一个 Jar → 运行一个命令 → 打开一个页面 → 开始部署流程** 🎯

前端页面和后端 API 都由同一个 Spring Boot 进程提供，真正做到 **一键打开、开箱即用** 💡

---

## 这次改了什么？🛠️

- ✅ 把部署控制台前端直接打进了 Java 包
- ✅ `jar` 启动后就能直接访问首页
- ✅ 不需要再额外跑 `AiOpsWeb`
- ✅ UI 和 API 改成同源访问，更简单、更稳
- ✅ 桌面环境下支持启动后自动打开浏览器 🌐

---

## 下载后怎么跑？📦

可直接运行的包就在这里：

```text
aiops-bootstrap/target/aiops-bootstrap-2.0.0-SNAPSHOT.jar
```

### 1. 准备环境

- `JDK 21`

### 2. 一条命令启动

```powershell
java -jar .\aiops-bootstrap\target\aiops-bootstrap-2.0.0-SNAPSHOT.jar
```

### 3. 默认访问地址

- 页面首页：`http://127.0.0.1:8080/`
- 部署执行接口：`http://127.0.0.1:8080/api/v1/ops/deploy/exec`
- 工作流接口：`http://127.0.0.1:8080/api/v1/workflow`

如果当前机器支持桌面环境，程序启动后会自动帮你打开浏览器 👀

---

## 30 秒自检一下 ⚡

先看看端口是不是起来了：

```powershell
Test-NetConnection -ComputerName 127.0.0.1 -Port 8080
```

再看看健康检查：

```powershell
curl http://127.0.0.1:8080/actuator/health
```

如果你看到了：

- `TcpTestSucceeded : True`
- `{"status":"UP"}`

那就说明这一个 Jar 已经完整跑起来啦 🎉

---

## 现在的使用方式是什么？🧭

Argus-Ops 当前推荐流程已经变成：

1. 输入项目目录 📁
2. 做本地准备与识别 🔎
3. 探测远端环境 🌍
4. 预览部署计划 🧾
5. 直接执行远程部署 🚀

整个过程都在同一个页面里完成，不需要来回切服务。

---

## AI 环境变量是可选的 🤖

如果你只是想先把程序跑起来，不配置真实 AI Key 也能启动。  
只是部分 AI 增强能力会跳过或降级。

```powershell
$env:LOCAL_AI_KEY="your-local-key"
$env:LOCAL_AI_BASE_URL="https://api.openai.com/v1"
$env:LOCAL_AI_MODEL="gpt-4o-mini"

$env:REMOTE_AI_KEY="your-remote-key"
$env:REMOTE_AI_BASE_URL="https://api.openai.com/v1"
$env:REMOTE_AI_MODEL="gpt-4o-mini"
```

---

## 如果你想自己重新打包 🔧

```powershell
mvn -DskipTests clean package
```

重新生成的可运行 Jar 在这里：

```text
aiops-bootstrap/target/aiops-bootstrap-2.0.0-SNAPSHOT.jar
```

---

## 这个项目现在能做什么？🧠

- 📦 本地项目准备与栈识别
- 🧪 远端主机探测
- 📝 部署预览生成
- 🔐 SSH 远程部署执行
- 📋 关键日志提取与诊断
- 🤖 AI 辅助解释复杂部署信息

---

## 给第一次上手的同学一句话 💬

别先和 Maven、Node、前后端联调流程较劲。  

**先下载 Jar，先跑起来，先打开页面。**  
只要能看到首页，你就已经跨过了最容易把人劝退的那一步 🙌
