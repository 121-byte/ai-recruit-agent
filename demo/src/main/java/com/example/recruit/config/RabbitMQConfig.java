package com.example.recruit.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 (复刻自文档 §11.1)。
 *
 * <p>仅在关闭 Mock 模式 (app.mock.enabled=false) 时装配，避免无 RabbitMQ 时启动失败。
 */
@Configuration
@ConditionalOnProperty(name = "app.mock.enabled", havingValue = "false")
public class RabbitMQConfig {

    public static final String ANALYSIS_EXCHANGE = "analysis.exchange";
    public static final String ANALYSIS_QUEUE = "analysis.queue";
    public static final String ANALYSIS_ROUTING_KEY = "analysis.task";

    @Bean
    public DirectExchange analysisExchange() {
        return new DirectExchange(ANALYSIS_EXCHANGE, true, false);
    }

    @Bean
    public Queue analysisQueue() {
        return QueueBuilder.durable(ANALYSIS_QUEUE).build();
    }

    @Bean
    public Binding analysisBinding(Queue analysisQueue, DirectExchange analysisExchange) {
        return BindingBuilder.bind(analysisQueue).to(analysisExchange).with(ANALYSIS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
