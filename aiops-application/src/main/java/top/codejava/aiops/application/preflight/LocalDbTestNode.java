// aiops-application/src/main/java/top/codejava/aiops/application/preflight/LocalDbTestNode.java
package top.codejava.aiops.application.preflight;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.codejava.aiops.type.preflight.PreflightContext;
import top.codejava.aiops.type.preflight.PreflightNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 本地数据库探测节点
 * 检查 H2 内嵌数据库是否可达
 */
@Slf4j
@Component
public class LocalDbTestNode implements PreflightNode {

    @Value("${spring.datasource.url:jdbc:h2:file:./data/aiopsdb}")
    private String jdbcUrl;

    @Value("${spring.datasource.username:sa}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Override
    public void check(PreflightContext context) {
        try {
            // 尝试获取连接探测数据库是否可用
            Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
            if (conn.isValid(5)) {
                log.info("Local database connection test passed");
            } else {
                context.addFinding("本地数据库连接验证失败: 连接无效");
            }
            conn.close();
        } catch (SQLException e) {
            log.warn("Local database connection failed: {}", e.getMessage());
            context.addFinding("本地数据库连接失败: " + e.getMessage());
            // H2 内嵌数据库通常会自动创建文件，这里只记录警告不阻止流程
        }
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String name() {
        return "LocalDbTest";
    }
}
