package ma.enset.soulaimane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class FraudAlertConsumer {
    private static final String TOPIC = "fraud-alerts";
    private static final String INFLUXDB_URL = "http://localhost:8086";
    private static final String INFLUXDB_TOKEN = "abcd1234";
    private static final String INFLUXDB_ORG = "org";
    private static final String INFLUXDB_BUCKET = "fraud_alerts";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        // Configure Kafka Consumer
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "fraud-alert-consumer");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Create InfluxDB client
        InfluxDBClient influxDBClient = InfluxDBClientFactory.create(
                INFLUXDB_URL,
                INFLUXDB_TOKEN.toCharArray(),
                INFLUXDB_ORG,
                INFLUXDB_BUCKET
        );

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
             WriteApi writeApi = influxDBClient.getWriteApi()) {

            consumer.subscribe(Collections.singletonList(TOPIC));
            System.out.println("Started consuming fraud alerts...");

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                records.forEach(record -> {
                    try {
                        // Parse the transaction
                        Transaction transaction = MAPPER.readValue(record.value(), Transaction.class);

                        // Create InfluxDB point
                        Point point = Point.measurement("fraud_transactions")
                                .addTag("userId", transaction.getUserId())
                                .addField("amount", transaction.getAmount())
                                .time(transaction.getTimestamp(), WritePrecision.S); // Using seconds precision

                        // Write to InfluxDB
                        writeApi.writePoint(point);

                        System.out.println("Stored fraud alert: " + transaction);
                    } catch (Exception e) {
                        System.err.println("Error processing record: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error in consumer: " + e.getMessage());
        } finally {
            influxDBClient.close();
        }
    }
}
