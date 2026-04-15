// aiops-cli/src/main/java/top/codejava/aiops/cli/command/DeployCommand.java
package top.codejava.aiops.cli.command;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import top.codejava.aiops.application.port.execution.DaemonInstallerPort;
import top.codejava.aiops.application.port.execution.OpsExecutorPort;
import top.codejava.aiops.domain.execution.ExecutionResult;
import top.codejava.aiops.domain.execution.ShellCommand;
import top.codejava.aiops.domain.execution.TargetServer;

import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * 部署命令
 * 支持双轨执行，自动检测 Daemon 并引导安装
 */
@CommandLine.Command(
        name = "deploy",
        description = "部署应用到目标服务器，自动检测并引导安装 AIOps Daemon",
        mixinStandardHelpOptions = true
)
@Component
@RequiredArgsConstructor
public class DeployCommand implements Callable<Integer> {

    @org.springframework.beans.factory.annotation.Qualifier("opsExecutorPort")
    private final OpsExecutorPort opsExecutor;
    private final DaemonInstallerPort daemonInstaller;

    @CommandLine.Option(
            names = {"--host"},
            description = "目标服务器IP或主机名",
            required = true
    )
    private String host;

    @CommandLine.Option(
            names = {"--ssh-port"},
            description = "SSH端口 (默认: 22)"
    )
    private int sshPort = 22;

    @CommandLine.Option(
            names = {"--rpc-port"},
            description = "Daemon RPC端口 (默认: 8765)"
    )
    private int rpcPort = 8765;

    @CommandLine.Option(
            names = {"--username"},
            description = "SSH用户名",
            required = true
    )
    private String username;

    @CommandLine.Option(
            names = {"--password"},
            description = "SSH密码（推荐使用密钥认证）"
    )
    private String password;

    @CommandLine.Option(
            names = {"--private-key"},
            description = "SSH私钥文件路径"
    )
    private String privateKeyPath;

    @CommandLine.Option(
            names = {"--command"},
            description = "要执行的部署命令",
            required = true
    )
    private String command;

    @CommandLine.Option(
            names = {"--workdir"},
            description = "工作目录"
    )
    private String workingDirectory;

    @Override
    public Integer call() {
        // 构建目标服务器对象
        TargetServer server = TargetServer.builder()
                .host(host)
                .sshPort(sshPort)
                .rpcPort(rpcPort)
                .username(username)
                .password(password)
                .privateKeyPath(privateKeyPath)
                .build();

        System.out.println();
        System.out.println("Target Server: " + host + ":" + sshPort);
        System.out.println("========================================");
        System.out.println();

        // 第一步：检查 Daemon 是否运行
        if (daemonInstaller.isDaemonRunning(server)) {
            System.out.println("\u001B[32m✓\u001B[0m AIOps Daemon is already running.");
            System.out.println();
            return executeCommand(command, server);
        }

        // Daemon 未运行，进入交互式引导
        System.out.println("\u001B[33m⚠\u001B[0m 检测到远程节点尚未部署 AIOps Daemon.");
        System.out.println();
        System.out.println("安装 Daemon 可以带来以下优势：");
        System.out.println("  • 更快的执行速度（保持长连接，无需重复握手）");
        System.out.println("  • 更好的 AI 上下文感知能力");
        System.out.println("  • 支持增量部署和状态持久化");
        System.out.println();

        // 交互式询问用户
        String choice = promptUserChoice();

        return switch (choice.toLowerCase()) {
            case "y" -> {
                // 自动安装
                System.out.println();
                System.out.println("Starting automatic installation...");
                boolean installed = daemonInstaller.installDaemon(server);
                if (installed) {
                    System.out.println("\u001B[32m✓\u001B[0m Daemon installed successfully!");
                    System.out.println();
                    yield executeCommand(command, server);
                } else {
                    System.out.println("\u001B[31m✗\u001B[0m Automatic installation failed.");
                    System.out.println();
                    System.out.println("Here's the manual installation script:");
                    System.out.println("----------------------------------------");
                    String script = daemonInstaller.generateManualInstallScript(server);
                    System.out.println(script);
                    System.out.println("----------------------------------------");
                    yield 1;
                }
            }
            case "manual" -> {
                // 输出手动安装脚本
                System.out.println();
                System.out.println("Manual installation script:");
                System.out.println("----------------------------------------");
                String script = daemonInstaller.generateManualInstallScript(server);
                System.out.println(script);
                System.out.println("----------------------------------------");
                System.out.println();
                System.out.println("After manual installation, re-run this command.");
                yield 0;
            }
            case "n" -> {
                // 不安装，直接使用 SSH 兜底
                System.out.println();
                System.out.println("Proceeding with direct SSH execution (no Daemon)...");
                System.out.println();
                yield executeCommand(command, server);
            }
            default -> {
                System.out.println("Invalid choice. Exiting.");
                yield 1;
            }
        };
    }

    /**
     * 提示用户选择安装方式
     */
    private String promptUserChoice() {
        System.out.print("是否授权本地通过 SSH 自动为您部署？(Y/N/Manual) > ");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return "n"; // 默认不安装
        }
        // 处理中文输入
        if (input.equals("是") || input.equals("y") || input.equals("Y")) {
            return "y";
        }
        if (input.startsWith("man") || input.equals("手动")) {
            return "manual";
        }
        return "n";
    }

    /**
     * 执行部署命令
     */
    private Integer executeCommand(String cmd, TargetServer server) {
        ShellCommand shellCommand = ShellCommand.builder()
                .arguments(java.util.List.of(cmd.split("\\s+")))
                .workingDirectory(workingDirectory)
                .build();

        System.out.println("Executing: " + cmd);
        System.out.println("----------------------------------------");

        ExecutionResult result = opsExecutor.execute(shellCommand, server);

        System.out.println("----------------------------------------");
        System.out.println();

        if (!result.getStdout().isEmpty()) {
            System.out.println("Output:");
            System.out.println(result.getStdout());
        }
        if (!result.getStderr().isEmpty()) {
            System.out.println("Stderr:");
            System.out.println(result.getStderr());
        }

        System.out.println();
        System.out.printf("Command exited with code %d in %dms%n",
                result.getExitCode(), result.getDurationMs());

        if (result.isSuccess()) {
            System.out.println("\u001B[32m✓ Deployment completed successfully\u001B[0m");
            return 0;
        } else {
            System.out.println("\u001B[31m✗ Deployment failed\u001B[0m");
            return result.getExitCode();
        }
    }
}
