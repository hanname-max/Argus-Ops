# 功能接口草案（重构阶段）

## 1. 生成构建脚本
- 路径: `POST /api/v1/ops/build-script`
- 请求体:
```json
{
  "projectPath": "D:/workspace/demo",
  "targetType": "linux-docker"
}
```
- 响应体:
```json
{
  "script": "#!/bin/bash\nmvn clean package ...",
  "summary": "Local AI generated build script",
  "usedRemoteAssist": false
}
```

## 2. 环境检查（本地优先，远程辅助）
- 路径: `POST /api/v1/ops/environment/check`
- 请求体:
```json
{
  "projectPath": "D:/workspace/demo"
}
```
- 响应体:
```json
{
  "checks": ["OS/Java/Port pre-check by local AI"],
  "risks": ["...local risk...", "...remote extra risk..."],
  "conclusion": "Local-first + remote-assist done"
}
```

## 3. 日志筛选与分析（本地主导）
- 路径: `POST /api/v1/ops/logs/filter`
- 请求体:
```json
{
  "rawLog": "...deploy error log..."
}
```
- 响应体:
```json
{
  "keyError": "Local AI extracted key error",
  "filteredLog": "...raw log...",
  "advice": "...local advice...\nRemote assist: ..."
}
```

## 4. 远程命令部署
- 路径: `POST /api/v1/ops/deploy/exec`
- 请求体:
```json
{
  "host": "192.168.1.10",
  "port": 22,
  "username": "root",
  "password": "******",
  "command": "bash /opt/deploy.sh"
}
```
- 响应体:
```json
{
  "success": true,
  "output": "...stdout...",
  "error": ""
}
```

## 5. 错误返回格式
```json
{
  "code": "AIOPS_400",
  "message": "projectPath is required"
}
```
