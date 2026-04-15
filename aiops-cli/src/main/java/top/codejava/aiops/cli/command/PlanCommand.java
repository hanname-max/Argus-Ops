// aiops-cli/src/main/java/top/codejava/aiops/cli/command/PlanCommand.java
package top.codejava.aiops.cli.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import top.codejava.aiops.application.usecase.GeneratePlanUseCase;
import top.codejava.aiops.domain.model.PlanDraft;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * 生成部署计划命令
 * 扫描当前目录并生成Docker化部署计划
 */
@CommandLine.Command(
        name = "plan",
        description = "扫描当前项目目录并生成部署计划",
        mixinStandardHelpOptions = true
)
@Component
@RequiredArgsConstructor
public class PlanCommand implements Callable<Integer> {

    private final GeneratePlanUseCase generatePlanUseCase;

    @CommandLine.Option(
            names = {"-p", "--path"},
            description = "项目根目录路径，默认为当前工作目录"
    )
    private Path path;

    @Override
    public Integer call() {
        Path targetPath = path != null ? path : Path.of("").toAbsolutePath();

        System.out.println();
        System.out.println("Scanning project at: " + targetPath);
        System.out.println("========================================");

        try {
            PlanDraft draft = generatePlanUseCase.execute(targetPath);

            System.out.println();
            System.out.println("\u001B[1mResult:\u001B[0m");
            System.out.println("========================================");
            System.out.println();

            if (draft.getArchitectureSummary() != null) {
                System.out.println("\u001B[1mArchitecture Summary:\u001B[0m");
                System.out.println(draft.getArchitectureSummary());
                System.out.println();
            }

            if (draft.getDockerfileContent() != null && !draft.getDockerfileContent().isEmpty()) {
                System.out.println("\u001B[1mGenerated Dockerfile:\u001B[0m");
                System.out.println("```dockerfile");
                System.out.println(draft.getDockerfileContent());
                System.out.println("```");
                System.out.println();
            }

            System.out.println("\u001B[1mSecurity Audit:\u001B[0m " +
                    (draft.isAuditPassed() ? "\u001B[32mPASSED\u001B[0m" : "\u001B[31mFAILED\u001B[0m"));

            if (!draft.isAuditPassed() && draft.getAuditFindings() != null && !draft.getAuditFindings().isEmpty()) {
                System.out.println("Findings:");
                for (String finding : draft.getAuditFindings()) {
                    System.out.println("  - " + finding);
                }
            }

            System.out.println();
            System.out.println("Done!");
            return 0;
        } catch (Exception e) {
            System.err.println("\u001B[31mError generating plan: " + e.getMessage() + "\u001B[0m");
            e.printStackTrace();
            return 1;
        }
    }
}
