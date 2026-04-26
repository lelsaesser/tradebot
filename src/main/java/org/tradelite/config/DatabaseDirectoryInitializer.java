package org.tradelite.config;

import jakarta.annotation.PostConstruct;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DatabaseDirectoryInitializer {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @PostConstruct
    void ensureDatabaseDirectory() {
        // Extract file path from jdbc:sqlite:<path>
        String prefix = "jdbc:sqlite:";
        if (!datasourceUrl.startsWith(prefix)) {
            return;
        }

        String dbPath = datasourceUrl.substring(prefix.length());
        if (dbPath.equals(":memory:")) {
            return;
        }

        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
            log.info("Created database directory: {}", parentDir.getAbsolutePath());
        }
    }
}
