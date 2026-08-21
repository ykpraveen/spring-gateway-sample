package com.example.gatewaysample.gateway.apikey;

import com.example.gatewaysample.gateway.apikey.dto.ApiClientPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class ApiKeyCacheConfig {

    @Bean
    public ReactiveRedisTemplate<String, ApiClientPrincipal> apiClientPrincipalRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        Jackson2JsonRedisSerializer<ApiClientPrincipal> valueSerializer =
                new Jackson2JsonRedisSerializer<>(ApiClientPrincipal.class);
        RedisSerializationContext<String, ApiClientPrincipal> context = RedisSerializationContext
                .<String, ApiClientPrincipal>newSerializationContext(new StringRedisSerializer())
                .value(valueSerializer)
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
