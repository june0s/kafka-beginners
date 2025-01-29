package io.conduktor.demos.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

public class ConsumerDemo {

    private static final Logger log = LoggerFactory.getLogger(ConsumerDemo.class.getSimpleName());

    public static void main(String[] args) {
        log.info("I'm a Kafka Consumer!");

        String groupId = "my-java-app";
        String topic = "demo_java";

        // create Producer Properties
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");

        // set consumer properties
        properties.setProperty("key.deserializer", StringDeserializer.class.getName());
        properties.setProperty("value.deserializer", StringDeserializer.class.getName());
        properties.setProperty("group.id", groupId);

        // none: 설정된 컨슈머 그룹이 없을 때 동작하지 않는다. 프로그램 실행 전에 컨슈머 그룹 설정해야 한다.
        // earliest: 토픽을 맨 처음부터 읽겠다. --from-beginning 옵션
        // latest: 지금부터 토픽을 읽겠다.
        properties.setProperty("auto.offset.reset", "earliest");

        // create the Consumer
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);

        // subscribe to a topic
        consumer.subscribe(Arrays.asList(topic));

        // poll for data
        while (true) {
            log.info("Polling...");

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000)); // 1 sec
            for (ConsumerRecord<String, String> record : records) {
                log.info("Key = " + record.key() + ", value = " + record.value());
                log.info("Partition = " + record.partition() + ", Offset = " + record.offset());
            }
        }
    }
}
