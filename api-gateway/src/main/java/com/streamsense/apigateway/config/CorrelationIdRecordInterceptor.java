package com.streamsense.apigateway.config;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

// Kafka listeners run on container threads outside any Reactor Context, so the producer-supplied header is put
// into MDC directly around each record, mirroring the servlet-style consumers in the other services.
public class CorrelationIdRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

    static final String CORRELATION_ID_HEADER = "correlationId";

    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        Header header = record.headers().lastHeader(CORRELATION_ID_HEADER);
        if (header != null && header.value() != null && header.value().length > 0) {
            MDC.put(CorrelationIdWebFilter.CORRELATION_ID_KEY, new String(header.value(), StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        MDC.remove(CorrelationIdWebFilter.CORRELATION_ID_KEY);
    }
}
