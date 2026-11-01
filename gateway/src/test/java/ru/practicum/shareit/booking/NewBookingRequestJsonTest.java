package ru.practicum.shareit.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.shareit.booking.dto.NewBookingRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class NewBookingRequestJsonTest {
    @Autowired
    private JacksonTester<NewBookingRequest> json;

    @Test
    void writesIsoDatesWithoutValidationHelper() throws Exception {
        NewBookingRequest request = new NewBookingRequest();
        request.setItemId(42L);
        request.setStart(LocalDateTime.of(2026, 11, 1, 12, 0));
        request.setEnd(LocalDateTime.of(2026, 11, 1, 14, 0));

        var result = json.write(request);

        assertThat(result).isEqualToJson("{\"itemId\":42,\"start\":\"2026-11-01T12:00:00\",\"end\":\"2026-11-01T14:00:00\"}")
            .doesNotHaveJsonPath("$.endAfterStart");
    }

    @Test
    void readsIsoDatesIntoLocalDateTime() throws Exception {
        NewBookingRequest request = json.parseObject("{\"itemId\":42,\"start\":\"2026-11-01T12:00:00\",\"end\":\"2026-11-01T14:00:00\"}");

        assertThat(request.getItemId()).isEqualTo(42L);
        assertThat(request.getStart()).isEqualTo(LocalDateTime.of(2026, 11, 1, 12, 0));
        assertThat(request.getEnd()).isEqualTo(LocalDateTime.of(2026, 11, 1, 14, 0));
    }
}
