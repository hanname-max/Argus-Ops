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
 * Deploy command
 * Supports dual-track execution, auto-detect Daemon and guide installation
 */
@CommandLine.Command(
        name = "deploy",
        description = "Deploy application to target server, auto-detect and install AIOps Daemon",
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
            description = "Target server IP or hostname",
            required = true
    )
    private String host;

    @CommandLine.Option(
            names = {"--ssh-port"},
            description = "SSH port (default: 22)"
    )
    private int sshPort = 22;

    @CommandLine.Option(
            names = {"--rpc-port"},
            description = "Daemon RPC port (default: 8765)"
    )
    private int rpcPort = 8765;

    @CommandLine.Option(
            names = {"--username"},
            description = "SSH username",
            required = true
    )
    private String username;

    @CommandLine.Option(
            names = {"--password"},
            description = "SSH password (key-based auth recommended)"
    )
    private String password;

    @CommandLine.Option(
            names = {"--private-key"},
            description = "SSH private key file path"
    )
    private String privateKeyPath;

    @CommandLine.Option(
            names = {"--command"},
            description = "Deployment command to execute",
            required = true
    )
    private String command;

    @CommandLine.Option(
            names = {"--workdir"},
            description = "Working directory"
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

        // Daemon not running, enter interactive guide
        System.out.println("\u001B[33m⚠\u001B[0m AIOps Daemon is not running on remote host.");
        System.out.println();
        System.out.println("Installing Daemon brings these benefits:");
        System.out.println("  • Faster execution (persistent connection, no repeated handshakes)");
        System.out.println("  • Better AI context awareness");
        System.out.println("  • Supports incremental deployment and state persistence");
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
     * Prompt user to choose installation method
     */
    private String promptUserChoice() {
        System.out.print("Allow local to auto-install via SSH? (Y/N/Manual) > ");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return "n"; // Default: do not install
        }
        // Handle both English and Chinese input
        if (input.equals("是") || input.equals("y") || input.equals("Y") || input.equals("yes")) {
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
