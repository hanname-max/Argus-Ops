#!/bin/bash

# AIOps-Engine 远程部署脚本 (Bash版本)
# 需要: maven, scp, ssh

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}      AIOps-Engine 远程部署工具${NC}"
echo -e "${GREEN}========================================${NC}"
echo
echo -e "远程配置信息:"
echo -e "  - 目标端口: ${YELLOW}20/tcp${NC} (HTTP)"
echo -e "  - 需要远程服务器已开放20端口防火墙"
echo -e "  - 需要远程Linux服务器已安装JDK 21"
echo

read -p "请输入远程服务器IP地址: " SERVER_IP
read -p "请输入远程SSH用户名 [默认: root]: " SSH_USER
if [ -z "$SSH_USER" ]; then
    SSH_USER="root"
fi

echo
echo -e "确认配置信息:"
echo -e "  远程服务器: ${GREEN}${SSH_USER}@${SERVER_IP}${NC}"
echo -e "  目标端口: 20"
echo -e "  远程目录: ~/aiops/"
echo

read -p "确认是否开始部署？ (y/N): " CONFIRM
if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
    echo -e "${YELLOW}已取消部署${NC}"
    exit 0
fi

echo
echo -e "${GREEN}正在编译项目...${NC}"
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}[错误] 编译失败，请检查错误信息重试${NC}"
    exit 1
fi

JAR_FILE="aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}[错误] 未找到编译产物 JAR 文件: $JAR_FILE${NC}"
    exit 1
fi

echo
echo -e "${GREEN}编译完成，开始上传...${NC}"
echo

# 创建远程目录并上传
ssh ${SSH_USER}@${SERVER_IP} "mkdir -p ~/aiops"
if [ $? -ne 0 ]; then
    echo -e "${RED}[错误] SSH连接失败，请检查服务器地址和权限${NC}"
    exit 1
fi

scp $JAR_FILE ${SSH_USER}@${SERVER_IP}:~/aiops/
if [ $? -ne 0 ]; then
    echo -e "${RED}[错误] 上传失败${NC}"
    exit 1
fi

echo
echo -e "${GREEN}上传完成！${NC}"
echo
echo -e "======================================================================"
echo -e "${YELLOW}请确认以下步骤在远程服务器执行：${NC}"
echo -e "======================================================================"
echo
echo "  1. 登录远程服务器:"
echo "    ssh ${SSH_USER}@${SERVER_IP}"
echo
echo "  2. 进入部署目录:"
echo "    cd ~/aiops"
echo
echo "  3. ${YELLOW}检查端口20是否被占用:${NC}"
echo "    lsof -i:20 || echo '端口20未被占用，可以启动'"
echo
echo "  4. ${YELLOW}启动应用:${NC}"
echo "    export ANTHROPIC_API_KEY=6bb65db7-422e-4e30-93f1-b17407cd35a7"
echo "    java -jar aiops-bootstrap-1.0.0-SNAPSHOT.jar"
echo
echo "  5. ${YELLOW}如需后台运行:${NC}"
echo "    nohup java -jar aiops-bootstrap-1.0.0-SNAPSHOT.jar > app.log 2>&1 &"
echo
echo -e "======================================================================"
echo
echo -e "部署完成后测试访问: ${GREEN}http://${SERVER_IP}/${NC}"
echo
