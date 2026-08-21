package com.everdeliver.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpStatusMapperTest {

    @Test
    void clientErrorsArePermanentExcept408And429() {
        assertThat(HttpStatusMapper.toDeliveryException(400, "bad", null))
                .isInstanceOf(PermanentDeliveryException.class);
        assertThat(HttpStatusMapper.toDeliveryException(404, "missing", null))
                .isInstanceOf(PermanentDeliveryException.class);
        assertThat(HttpStatusMapper.toDeliveryException(408, "timeout", null))
                .isInstanceOf(RetryableDeliveryException.class);
        assertThat(HttpStatusMapper.toDeliveryException(429, "slow down", null))
                .isInstanceOf(RetryableDeliveryException.class);
    }

    @Test
    void serverErrorsAreRetryable() {
        assertThat(HttpStatusMapper.toDeliveryException(500, "boom", null))
                .isInstanceOf(RetryableDeliveryException.class);
        assertThat(HttpStatusMapper.toDeliveryException(503, "unavailable", null))
                .isInstanceOf(RetryableDeliveryException.class);
    }
}
