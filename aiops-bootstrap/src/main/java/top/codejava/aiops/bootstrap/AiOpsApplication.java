package top.codejava.aiops.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "top.codejava.aiops")
public class AiOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiOpsApplication.class, args);
    }
}
