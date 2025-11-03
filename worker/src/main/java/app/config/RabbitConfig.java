package app.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.util.UUID;

@Configuration
public class RabbitConfig {

    public static final String PROCESS_QUEUE = "chat.process.queue";
    public static final String REPL_EXCHANGE = "chat.replication.exchange";
    public static final String REPL_QUEUE_PREFIX = "chat.replication.queue";
    public static final String STREAM_QUEUE_PREFIX = "chat.stream.queue";

    @Bean
    public Queue processQueue() {
        return new Queue(PROCESS_QUEUE, true);
    }

    @Bean
    public FanoutExchange replicationExchange() {
        return new FanoutExchange(REPL_EXCHANGE, true, false);
    }

    @Bean
    public Queue replicationQueue() {
        String hostname = "unknown";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}
        hostname = hostname.replaceAll("[^a-zA-Z0-9_.-]", "_");

        String uniqueQueueName = REPL_QUEUE_PREFIX + "." + hostname + "." + UUID.randomUUID();
        System.out.println("[Worker RabbitConfig] Declared ephemeral replication queue: " + uniqueQueueName);
        return new Queue(uniqueQueueName, true, false, true);
    }

    @Bean
    public Binding replicationBinding(Queue replicationQueue, FanoutExchange replicationExchange) {
        System.out.printf("[RabbitConfig] Bound %s → %s%n", replicationQueue.getName(), REPL_EXCHANGE);
        return BindingBuilder.bind(replicationQueue).to(replicationExchange);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate rt = new RabbitTemplate(connectionFactory);
        rt.setMessageConverter(converter);
        return rt;
    }
    @Bean
    public Queue streamQueue() {
        String hostname = "unknown";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}
        hostname = hostname.replaceAll("[^a-zA-Z0-9_.-]", "_");

        // Unique queue per producer instance
        String queueName = "chat.stream.queue." + hostname + "." + UUID.randomUUID();
        System.out.println("[RabbitConfig] Declared ephemeral stream queue: " + queueName);
        return new Queue(queueName, true, false, true);
    }


}
