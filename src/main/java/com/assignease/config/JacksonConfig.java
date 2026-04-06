package com.assignease.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    // LocalDate format: "2025-06-15" — date only, NO timezone needed
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // LocalDateTime format: "2025-06-15T14:30:00.000Z" — UTC ISO-8601
    // Browsers parse this and display in USER's local timezone automatically
    private static final DateTimeFormatter DATETIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        javaTimeModule.addSerializer(
            java.time.LocalDate.class,
            new LocalDateSerializer(DATE_FMT)
        );
        javaTimeModule.addDeserializer(
            java.time.LocalDate.class,
            new LocalDateDeserializer(DATE_FMT)
        );
        javaTimeModule.addSerializer(
            java.time.LocalDateTime.class,
            new LocalDateTimeSerializer(DATETIME_FMT)
        );
        javaTimeModule.addDeserializer(
            java.time.LocalDateTime.class,
            new LocalDateTimeDeserializer(DATETIME_FMT)
        );

        return new ObjectMapper()
            .registerModule(javaTimeModule)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
