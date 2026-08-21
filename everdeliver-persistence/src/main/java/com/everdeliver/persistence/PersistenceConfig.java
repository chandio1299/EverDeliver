package com.everdeliver.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@EntityScan(basePackageClasses = Notification.class)
@EnableJpaRepositories(basePackageClasses = NotificationRepository.class)
public class PersistenceConfig {
}
