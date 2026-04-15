// aiops-bootstrap/src/main/java/top/codejava/aiops/bootstrap/AiOpsApplication.java
package top.codejava.aiops.bootstrap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * AIOps-Engine Bootstrap Entry
 * Build CLI application using Spring Boot + Picocli
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
        // 零配置终端编码自适应：解决Windows CMD中文乱码问题
        adaptConsoleEncoding();
        SpringApplication.run(AiOpsApplication.class, args);
    }

    /**
     * Adaptive console encoding: automatically adapt UTF-8 output in Windows CMD environment
     * No third-party dependencies, no user environment changes required, zero-config adaptation
     */
    private static void adaptConsoleEncoding() {
        // Only adapt on Windows systems
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            return;
        }

        try {
            // Get current console encoding
            String consoleEncoding = System.getProperty("sun.stdout.encoding");
            if (consoleEncoding == null) {
                consoleEncoding = System.getProperty("file.encoding");
            }

            // If already UTF-8, no need to process
            if ("UTF-8".equalsIgnoreCase(consoleEncoding) || "UTF8".equalsIgnoreCase(consoleEncoding)) {
                return;
            }

            // Re-create System.out with UTF-8 encoding
            // This allows correct UTF-8 output even if CMD defaults to GBK
            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream utf8Out = new java.io.PrintStream(originalOut, true, java.nio.charset.StandardCharsets.UTF_8);
            System.setOut(utf8Out);

            // Same process for System.err
            java.io.PrintStream originalErr = System.err;
            java.io.PrintStream utf8Err = new java.io.PrintStream(originalErr, true, java.nio.charset.StandardCharsets.UTF_8);
            System.setErr(utf8Err);

        } catch (Exception e) {
            // Any exception fails silently, doesn't affect normal startup
            // Worst case: still garbled, but won't crash
        }
    }

    @Override
    public void run(String... args) {
        CommandLine commandLine = new CommandLine(new RootCommand(), commandLineFactory);
        commandRegistrar.registerCommands(commandLine);
        int exitCode = commandLine.execute(args);
        // Exit after CLI execution completes
        System.exit(exitCode);
    }

    /**
     * Root command container
     */
    @picocli.CommandLine.Command(name = "aiops",
                                 description = "AIOps-Engine - Dual-AI collaborative automated operation and maintenance assistant",
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
     * Command Registrar
     * Register all CLI commands under the root command
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
