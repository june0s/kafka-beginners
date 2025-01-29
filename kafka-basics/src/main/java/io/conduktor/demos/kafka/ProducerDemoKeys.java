package io.conduktor.demos.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class ProducerDemoKeys {

    private static final Logger log = LoggerFactory.getLogger(ProducerDemoKeys.class.getSimpleName());

    public static void main(String[] args) {
        log.info("I'm a Kafka producer!");

        // create Producer Properties
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");

        // set producer properties
        properties.setProperty("key.serializer", StringSerializer.class.getName());
        properties.setProperty("value.serializer", StringSerializer.class.getName());

        // create the Producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

        // 같은 key 를 가진 메시지는 같은 partition 으로 가는지 테스트
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 10; i++) {
                String topic = "demo_java";
                String key = "id_" + i;
                String value = "hello " + i;

                // create a Producer Record
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);

                // send data
                producer.send(record, new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata metadata, Exception e) {
                        // executes every time a record successfully sent or an exception is thrown
                        if (e == null) {
                            // the record was successfully sent
                            log.info("[callback] key = " + key + " | Partition = " + metadata.partition());
                        } else {
                            log.error("Error while producing = ", e);
                        }
                    }
                });
            }
        }

        // tell the producer to send all data and block until done -- synchronous
        producer.flush();

        // flush and close the producer
        producer.close();

        log.info("Data sent successfully!");
    }
}
