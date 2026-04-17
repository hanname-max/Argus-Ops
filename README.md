# AIOps Spring AI Rebuild

- 多模块架构，完全移除 LangChain。
- 本地 AI 主导：构建脚本生成、环境检查、日志过滤。
- 远程 AI 辅助：关键风险补充与日志优先级判断。
- 远程部署：通过 SSH 执行命令。

## 快速启动

```bash
mvn -DskipTests clean package
mvn -f aiops-bootstrap/pom.xml spring-boot:run
```
