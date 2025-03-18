package com.mb.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.jdbc.JdbcMessageHandler;
import org.springframework.integration.jdbc.JdbcPollingChannelAdapter;
import org.springframework.integration.jdbc.MessagePreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
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
    JdbcMessageHandler jdbcMessageHandler(DataSource dataSource) {
        JdbcMessageHandler jdbcMessageHandler = new JdbcMessageHandler(dataSource, "update account_statement set status = 'CREATED' where id = ?");
        jdbcMessageHandler.setPreparedStatementSetter(new MessagePreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, Message<?> requestMessage) throws SQLException {
                var accountStatement = (AccountStatement)requestMessage.getPayload();
                ps.setObject(1, accountStatement.id());
                ps.execute();
            }
        });
        return jdbcMessageHandler;
    }
    @Bean
    IntegrationFlow integrationFlow(JdbcPollingChannelAdapter inbound, JdbcMessageHandler outbound){
        return IntegrationFlow.from(inbound, poller-> poller.poller(pm->pm.fixedRate(1000)))
                .split()
                .handle(new GenericHandler<AccountStatement>() {
                    @Override
                    public Object handle(AccountStatement payload, MessageHeaders headers) {
                            log.info("*".repeat(50));
                            log.info(payload.toString());
                            headers.forEach((k,v)->{log.info(k+":"+v);});
                        return payload;
                    }
                })
                .handle(outbound)
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


