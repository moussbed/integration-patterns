package com.mb.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.jdbc.JdbcPollingChannelAdapter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;


@IntegrationComponentScan
@SpringBootApplication
public class IntegrationApplication {
    Logger log = Logger.getLogger(IntegrationApplication.class.getName());

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(IntegrationApplication.class, args);
		Thread.currentThread().join();
    }

    @Bean
    JdbcPollingChannelAdapter jdbcPollingChannelAdapter(DataSource dataSource, AccountStatementRowMapper accountStatementRowMapper) {
        var jdbcPollingChannelAdapter = new JdbcPollingChannelAdapter(dataSource, "select * from account_statement where balance IS NOT NULL");
        jdbcPollingChannelAdapter.setRowMapper(accountStatementRowMapper);
        jdbcPollingChannelAdapter.setUpdateSql("update account_statement set status = 'MODIFIED'  where id= :id ");
        jdbcPollingChannelAdapter.setUpdatePerRow(true);
        jdbcPollingChannelAdapter.setUpdateSqlParameterSourceFactory(input -> {
            if(input instanceof AccountStatement accountStatement) {
                return new MapSqlParameterSource("id", accountStatement.id());
            }
            return null;
        });
        return jdbcPollingChannelAdapter;
    }
    @Bean
    IntegrationFlow integrationFlow(JdbcPollingChannelAdapter jdbcPollingChannelAdapter){
        return IntegrationFlow.from(jdbcPollingChannelAdapter, poller-> poller.poller(pm->pm.fixedRate(1000)))
                .handle(new GenericHandler<List<AccountStatement>>() {
                    @Override
                    public Object handle(List<AccountStatement> accountStatementList, MessageHeaders headers) {
                        for (AccountStatement payload : accountStatementList) {
                            log.info("*".repeat(50));
                            log.info(payload.toString());
                        }
                        return null;
                    }
                })
                .get();
    }

}

@Component
class AccountStatementRowMapper  implements RowMapper<AccountStatement>{

    @Override
    public AccountStatement mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AccountStatement(
                UUID.fromString(rs.getString("id")),
                rs.getString("account_number"),
                AccountOperation.valueOf(rs.getString("account_operation")),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance"),
                rs.getObject("created_date", LocalDateTime.class),
                rs.getObject("modified_date", LocalDateTime.class),
                StatementStatus.valueOf(rs.getString("statement_status")),
                Status.valueOf(rs.getString("status"))
        );
    }
}

record AccountStatement(UUID id,
                        String accountNumber,
                        AccountOperation accountOperation,
                        BigDecimal amount,
                        BigDecimal balance,
                        LocalDateTime createdDate,
                        LocalDateTime modifiedDate,
                        StatementStatus statementStatus,
                        Status status
) {}

enum StatementStatus {
    ACCEPTED, PENDING, FAILED
}
enum Status {
    CREATED, MODIFIED
}
enum AccountOperation {
    DEBIT, CREDIT
}


