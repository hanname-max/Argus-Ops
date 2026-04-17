// aiops-bootstrap/src/main/java/top/codejava/aiops/bootstrap/AiOpsApplication.java
package top.codejava.aiops.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AIOps-Engine Bootstrap Entry
 * Web 应用入口，提供 REST API 服务
 */
@SpringBootApplication(scanBasePackages = "top.codejava.aiops")
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = "top.codejava.aiops")
@org.springframework.boot.autoconfigure.domain.EntityScan(basePackages = "top.codejava.aiops")
public class AiOpsApplication {

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
}
