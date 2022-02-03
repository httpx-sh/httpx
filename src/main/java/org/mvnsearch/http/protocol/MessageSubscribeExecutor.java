package org.mvnsearch.http.protocol;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.mvnsearch.http.logging.HttpxErrorCodeLogger;
import org.mvnsearch.http.logging.HttpxErrorCodeLoggerFactory;
import org.mvnsearch.http.model.HttpRequest;

import java.net.URI;
import java.util.*;


public class MessageSubscribeExecutor implements BaseExecutor {
    private static final HttpxErrorCodeLogger log = HttpxErrorCodeLoggerFactory.getLogger(MessageSubscribeExecutor.class);

    @Override
    public List<byte[]> execute(HttpRequest httpRequest) {
        final URI realURI = URI.create(httpRequest.getRequestTarget().getUri().getSchemeSpecificPart());
        if (Objects.equals(realURI.getScheme(), "kafka")) {
            subscribeKafka(realURI, httpRequest);
        } else {
            System.err.println("Not support: " + realURI);
        }
        return Collections.emptyList();
    }

    public void subscribeKafka(URI kafkaURI, HttpRequest httpRequest) {
        Properties props = new Properties();
        int port = kafkaURI.getPort();
        if (port <= 0) {
            port = 9092;
        }
        String topic = kafkaURI.getPath().substring(1);
        final Map<String, String> params = queryToMap(kafkaURI);
        String groupId = "httpx-consumer";
        if (params.containsKey("group")) {
            groupId = params.get("group");
        }
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaURI.getHost() + ":" + port);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            // shutdown hook to properly close the consumer
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    consumer.close();
                } catch (Exception ignore) {

                }
                System.out.println("Shutting down ...");
            }));
            System.out.println("Succeeded to subscribe(1000 max): " + topic + "!");
            // max message count to process
            int counter = 0;
            do {
                ConsumerRecords<String, String> records = consumer.poll(10000);
                for (ConsumerRecord<String, String> record : records) {
                    if (counter > 0) {
                        System.out.println("==================================");
                    }
                    String key = record.key();
                    System.out.println("Received message: " + (key == null ? "" : key));
                    System.out.println(record.value());
                    counter++;
                }
            } while (counter <= 1000);
        } catch (Exception e) {
            log.error("HTX-106-500", httpRequest.getRequestTarget().getUri(), e);
        }
    }
}