// aiops-bootstrap/src/main/java/top/codejava/aiops/bootstrap/AiOpsApplication.java
package top.codejava.aiops.bootstrap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * AIOps-Engine 启动入口
 * 使用 Spring Boot + Picocli 构建命令行应用
 */
@SpringBootApplication(scanBasePackages = "top.codejava.aiops")
public class AiOpsApplication implements CommandLineRunner {

    private final CommandLine.IFactory commandLineFactory;
    private final CommandRegistrar commandRegistrar;

    public AiOpsApplication(CommandLine.IFactory commandLineFactory,
                           CommandRegistrar commandRegistrar) {
        this.commandLineFactory = commandLineFactory;
        this.commandRegistrar = commandRegistrar;
    }

    public static void main(String[] args) {
        SpringApplication.run(AiOpsApplication.class, args);
    }

    @Override
    public void run(String... args) {
        CommandLine commandLine = new CommandLine(new RootCommand(), commandLineFactory);
        commandRegistrar.registerCommands(commandLine);
        commandLine.execute(args);
    }

    /**
     * 根命令容器
     */
    @picocli.CommandLine.Command(name = "aiops",
                                 description = "AIOps-Engine - 双AI协同自动化运维辅助工具",
                                 version = "1.0.0-SNAPSHOT",
                                 mixinStandardHelpOptions = true)
    public static class RootCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            // 无参数时显示帮助
            return 0;
        }
    }

    /**
     * 命令注册器
     * 将所有CLI命令注册到根命令下
     */
    @org.springframework.stereotype.Component
    public static class CommandRegistrar {
        private final top.codejava.aiops.cli.command.PlanCommand planCommand;
        private final top.codejava.aiops.cli.command.DeployCommand deployCommand;

        public CommandRegistrar(top.codejava.aiops.cli.command.PlanCommand planCommand,
                               top.codejava.aiops.cli.command.DeployCommand deployCommand) {
            this.planCommand = planCommand;
            this.deployCommand = deployCommand;
        }

        public void registerCommands(CommandLine rootCommand) {
            rootCommand.addSubcommand(planCommand);
            rootCommand.addSubcommand(deployCommand);
        }
    }
}
