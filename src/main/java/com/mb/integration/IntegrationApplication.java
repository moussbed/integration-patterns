package com.mb.integration;

import org.postgresql.jdbc.PgConnection;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.jdbc.JdbcMessageHandler;
import org.springframework.integration.jdbc.JdbcPollingChannelAdapter;
import org.springframework.integration.jdbc.MessagePreparedStatementSetter;
import org.springframework.integration.jdbc.channel.PgConnectionSupplier;
import org.springframework.integration.jdbc.channel.PostgresChannelMessageTableSubscriber;
import org.springframework.integration.jdbc.channel.PostgresSubscribableChannel;
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore;
import org.springframework.integration.jdbc.store.channel.PostgresChannelMessageStoreQueryProvider;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.IntStream;


@IntegrationComponentScan
@SpringBootApplication
public class IntegrationApplication {
    Logger log = Logger.getLogger(IntegrationApplication.class.getName());

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(IntegrationApplication.class, args);
		Thread.currentThread().join();
    }

    @Bean
    JdbcChannelMessageStore jdbcMessageStore(DataSource dataSource) {
        var jdbcChannelMessageStore = new JdbcChannelMessageStore(dataSource);
        jdbcChannelMessageStore.setChannelMessageStoreQueryProvider(new PostgresChannelMessageStoreQueryProvider());
        return jdbcChannelMessageStore;
    }

    @Bean
    MessageChannel outputChannel(JdbcChannelMessageStore messageStore) {
        return MessageChannels.queue(messageStore, "bootiful-group").getObject();
    }
    @Bean
    ApplicationRunner runner(MessageChannel outputChannel) {
        return args -> {
            IntStream.range(0, 30).forEach(i -> outputChannel.send(MessageBuilder.withPayload("Hello Postgres " + i).build()));
        };
    }

    @Bean
    PostgresChannelMessageTableSubscriber subscriber(DataSourceProperties dsp) throws SQLException {
       var supplier =  DriverManager.getConnection(
               dsp.determineUrl(), dsp.determineUsername(), dsp.determinePassword())
               .unwrap(PgConnection.class);
      PgConnectionSupplier pgConnectionSupplier = () -> supplier;
        return new PostgresChannelMessageTableSubscriber(pgConnectionSupplier);
    }

    @Bean
    MessageChannel in(PostgresChannelMessageTableSubscriber subscriber, JdbcChannelMessageStore messageStore) {
        return new PostgresSubscribableChannel(messageStore, "bootiful-group", subscriber);
    }

    @ServiceActivator(inputChannel = "in")
    public void handleMessage(Message<String> message) {
        log.info("Received Message : " + message.getPayload());
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
                .aggregate()
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


