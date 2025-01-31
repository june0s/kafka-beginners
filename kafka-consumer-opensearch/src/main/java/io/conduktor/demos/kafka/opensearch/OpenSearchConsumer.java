package io.conduktor.demos.kafka.opensearch;

import com.google.gson.JsonParser;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class OpenSearchConsumer {

    public static RestHighLevelClient createOpenSearchClient() throws URISyntaxException {
        String connString = "http://localhost:9200";

        // we build a URI from the connection string
        RestHighLevelClient restHighLevelClient;
        URI connUri = new URI(connString);

        // extract login information if it exists
        String userInfo = connUri.getUserInfo();

        if (userInfo == null) {
            // REST client without security
            restHighLevelClient = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(connUri.getHost(), connUri.getPort(), "http")));
        } else {
            // REST client with security
            String[] auth = userInfo.split(":");

            CredentialsProvider cp = new BasicCredentialsProvider();
            cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(auth[0], auth[1]));

            restHighLevelClient = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(connUri.getHost(), connUri.getPort(), connUri.getScheme()))
                            .setHttpClientConfigCallback(
                                    httpAsyncClientBuilder -> httpAsyncClientBuilder
                                            .setDefaultCredentialsProvider(cp)
                                            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
                            )
            );
        }
        return restHighLevelClient;
    }

    private static KafkaConsumer<String, String> createKafkaConsumer() {
        String groupId = "consumer-opensearch-demo";

        // create Producer Properties
        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // 저동 커밋 비활성화.

        // create the Consumer
        return new KafkaConsumer<>(properties);
    }

    private static String extractId(String json) {

        return JsonParser.parseString(json)
                .getAsJsonObject()
                .get("meta")
                .getAsJsonObject()
                .get("id")
                .getAsString();
    }

    public static void main(String[] args) throws URISyntaxException, IOException {
        Logger log = LoggerFactory.getLogger(OpenSearchConsumer.class.getSimpleName());

        String topic = "wikimedia.recentchange";

        // first create an OpenSearch client
        final RestHighLevelClient openSearchClient = createOpenSearchClient();

        // create our kafka client
        final KafkaConsumer<String, String> consumer = createKafkaConsumer();

        // try 문에 openSearchClient를 전달하면, openSearchClient.close() 가 호출된다고 한다. java magic!
        try (openSearchClient; consumer) {
            String index = "wikimedia";

            final boolean indexExists = openSearchClient.indices().exists(new GetIndexRequest(index), RequestOptions.DEFAULT);
            if (!indexExists) {
                // we need to create the index on OpenSearch if it doesn't exist already
                final CreateIndexRequest createIndexRequest = new CreateIndexRequest(index);
                final CreateIndexResponse response = openSearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
                log.info("The Wikimedia index has been created. ack = " + response.isAcknowledged());
            } else {
                log.info("The Wikimedia index already exists.");
            }

            // we subscribe the consumer
            consumer.subscribe(Collections.singleton(topic));
            log.info("Subscribed to the Kafka topic.");

            while (true) {
                final ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(3000));

                final int recordCount = records.count();
                log.info("Recv " + recordCount + " record(s).");

                // - 대량 요청하기 (객체 생성)
                BulkRequest bulkRequest = new BulkRequest();

                // send to openSearch (record 하나씩 보내기)
                for (ConsumerRecord<String, String> record : records) {
                    log.debug("+ record = " + record.value() + ", partition = " + record.partition() + ", topic = " + record.topic());
                    try {
                        // 멱등 전략 1
                        // define an ID using Kafka Record coordinates
//                        String id = record.topic()  + "_" + record.partition() + "_" + record.offset();

                        // 멱등 전략 2
                        // wikimedia json meta 데이터에 있는 id 를 사용.
                        // id가 중복되면, opensearch 가 해당 id 값을 덮어쓴다.
                        String id = extractId(record.value());

                        // send to record into openSearch
                        final IndexRequest indexRequest = new IndexRequest(index)
                                .source(record.value(), XContentType.JSON)
                                .id(id);

                        // - 대량 요청하기 (indexRequest 쌓기)
//                        final IndexResponse response = openSearchClient.index(indexRequest, RequestOptions.DEFAULT);
//                        log.info("Inserted 1 document into OpenSearch ID = " + response.getId());
                        bulkRequest.add(indexRequest);
                    } catch (Exception e) {
                        log.error("Error = " + e);
                    }
                }
                // - 대량 요청하기 (openSearch 보내기)
                if (0 < bulkRequest.numberOfActions()) {
                    final BulkResponse bulkResponse = openSearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                    log.info("Inserted " + bulkResponse.getItems().length + " record(s).");

                    // 지연을 추가해 대량 작업 수행하게 하기.
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // commit offsets after the batch is consumed -> at least once 방법.
                    consumer.commitSync();
                    log.info("Offsets have been committed!");
                }
            }
        }

        // create our Kafka client

        // main code logic

        // close things
    }
}
