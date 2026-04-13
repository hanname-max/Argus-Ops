// aiops-domain/src/main/java/top/codejava/aiops/domain/execution/TargetServer.java
package top.codejava.aiops.domain.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 目标服务器领域实体
 * 存储远程目标服务器的连接信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetServer {

    /**
     * 服务器IP地址或主机名
     */
    private String host;

    /**
     * SSH 端口（默认 22）
     */
    @Builder.Default
    private int sshPort = 22;

    /**
     * Daemon RPC 端口（默认 8765）
     */
    @Builder.Default
    private int rpcPort = 8765;

    /**
     * SSH 登录用户名
     */
    private String username;

    /**
     * SSH 密码（可选，优先使用密钥认证）
     */
    private String password;

    /**
     * SSH 私钥文件路径（可选）
     */
    private String privateKeyPath;

    /**
     * 私钥密码短语（可选，加密的私钥需要）
     */
    private String passphrase;

    /**
     * 服务器是否为 Linux 系统
     */
    @Builder.Default
    private boolean isLinux = true;

    /**
     * 获取 RPC 连接地址
     */
    public String getRpcBaseUrl() {
        return String.format("http://%s:%d", host, rpcPort);
    }
}
