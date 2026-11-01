package ru.practicum.shareit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ShareItGateway.class)
class ShareItGatewayTests {
    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithoutDatabase() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
    }
}
