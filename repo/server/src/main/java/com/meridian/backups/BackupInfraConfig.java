package com.meridian.backups;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BackupInfraConfig {

    @Bean
    public ProcessExecutor processExecutor() {
        return ProcessExecutor.system();
    }
}
