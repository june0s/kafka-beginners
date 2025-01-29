package io.conduktor.demos.kafka;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class ProducerDemoWithCallback {

    private static final Logger log = LoggerFactory.getLogger(ProducerDemoWithCallback.class.getSimpleName());

    public static void main(String[] args) {
        log.info("I'm a Kafka producer!");

        // create Producer Properties
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");

        // set producer properties
        properties.setProperty("key.serializer", StringSerializer.class.getName());
        properties.setProperty("value.serializer", StringSerializer.class.getName());

        // bath size 줄이기 (default: 16KBytes) -> 설정한 사이즈가 되면, Partition 을 변경한다.
        properties.setProperty("batch.size", "400");

        // partition 클래스를 RoundRobin 으로 변경할 수도 있다. 권장 안 함!
//        properties.setProperty("partitioner.class", RoundRobinPartitioner.class.getName());

        // create the Producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

        // sticky partition 동작 확인을 위해 loop 돌며 메시지 전송
        for (int j = 0; j < 10;j++) {
            for (int i = 0; i < 30; i++) {
                // create a Producer Record
                ProducerRecord<String, String> record = new ProducerRecord<>("demo_java", "hello world j="+ j + ", i=" +i);

                // send data
                producer.send(record, new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata metadata, Exception e) {
                        // executes every time a record successfully sent or an exception is thrown
                        if (e == null) {
                            // the record was successfully sent
                            log.info("Recv new metadata \n" +
                                    "Topic: " + metadata.topic() + "\n" +
                                    "Partition: " + metadata.partition() + "\n" +
                                    "Offset: " + metadata.offset() + "\n" +
                                    "Timestamp: " + metadata.timestamp() + "\n"
                            );
                        } else {
                            log.error("Error while producing = ", e);
                        }
                    }
                });
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }


        // tell the producer to send all data and block until done -- synchronous
        producer.flush();

        // flush and close the producer
        producer.close();

        log.info("Data sent successfully!");
    }
}
