package com.koushik.course_catalog.security.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 28800)
@Profile("!test")
public class SessionConfig {

    private static final String CREATE_PERSISTENT_LOGINS_SQL =
            "CREATE TABLE IF NOT EXISTS persistent_logins (" +
            "username varchar(64) not null, " +
            "series varchar(64) primary key, " +
            "token varchar(64) not null, " +
            "last_used timestamp not null)";

    private static final String CREATE_SPRING_SESSION_SQL =
            "CREATE TABLE IF NOT EXISTS SPRING_SESSION (" +
            "PRIMARY_ID CHAR(36) NOT NULL, " +
            "SESSION_ID CHAR(36) NOT NULL, " +
            "CREATION_TIME BIGINT NOT NULL, " +
            "LAST_ACCESS_TIME BIGINT NOT NULL, " +
            "MAX_INACTIVE_INTERVAL INT NOT NULL, " +
            "EXPIRY_TIME BIGINT NOT NULL, " +
            "PRINCIPAL_NAME VARCHAR(100), " +
            "CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID))";

    private static final String CREATE_SPRING_SESSION_IX1_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID)";

    private static final String CREATE_SPRING_SESSION_IX2_SQL =
            "CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME)";

    private static final String CREATE_SPRING_SESSION_IX3_SQL =
            "CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME)";

    private static final String CREATE_SPRING_SESSION_ATTRIBUTES_SQL =
            "CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (" +
            "SESSION_PRIMARY_ID CHAR(36) NOT NULL, " +
            "ATTRIBUTE_NAME VARCHAR(200) NOT NULL, " +
            "ATTRIBUTE_BYTES BYTEA NOT NULL, " +
            "CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME), " +
            "CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) " +
            "REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE)";

    @Bean
    PersistentTokenRepository persistentTokenRepository(
            @Qualifier("courseDataSource") DataSource courseDataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(courseDataSource);
        jdbc.execute(CREATE_SPRING_SESSION_SQL);
        jdbc.execute(CREATE_SPRING_SESSION_IX1_SQL);
        jdbc.execute(CREATE_SPRING_SESSION_IX2_SQL);
        jdbc.execute(CREATE_SPRING_SESSION_IX3_SQL);
        jdbc.execute(CREATE_SPRING_SESSION_ATTRIBUTES_SQL);
        jdbc.execute(CREATE_PERSISTENT_LOGINS_SQL);

        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(courseDataSource);
        repository.setCreateTableOnStartup(false);
        return repository;
    }

    @Bean(name = "springSessionTransactionOperations")
    TransactionOperations springSessionTransactionOperations(
            @Qualifier("courseTransactionManager") PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
