/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.kafka;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaProducerFullTest extends BaseEmbeddedKafkaTest {

    private static final String TOPIC_STRINGS = "test";
    private static final String TOPIC_STRINGS_IN_HEADER = "testHeader";
    private static final String TOPIC_BYTES = "testBytes";
    private static final String TOPIC_BYTES_IN_HEADER = "testBytesHeader";
    private static final String GROUP_STRINGS = "groupStrings";
    private static final String GROUP_BYTES = "groupStrings";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerFullTest.class);

    private static KafkaConsumer<String, String> stringsConsumerConn;
    private static KafkaConsumer<byte[], byte[]> bytesConsumerConn;

    @EndpointInject(uri = "kafka:localhost:{{karfkaPort}}?topic=" + TOPIC_STRINGS
            + "&requestRequiredAcks=-1")
    private Endpoint toStrings;

    @EndpointInject(uri = "kafka:localhost:{{karfkaPort}}?topic=" + TOPIC_BYTES + "&requestRequiredAcks=-1"
            + "&serializerClass=org.apache.kafka.common.serialization.ByteArraySerializer&"
            + "keySerializerClass=org.apache.kafka.common.serialization.ByteArraySerializer")
    private Endpoint toBytes;

    @Produce(uri = "direct:startStrings")
    private ProducerTemplate stringsTemplate;

    @Produce(uri = "direct:startBytes")
    private ProducerTemplate bytesTemplate;

    @BeforeClass
    public static void before() {
        Properties stringsProps = new Properties();

        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + getKarfkaPort());
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, "DemoConsumer");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        stringsConsumerConn = new KafkaConsumer<String, String>(stringsProps);

        Properties bytesProps = new Properties();
        bytesProps.putAll(stringsProps);
        bytesProps.put("group.id", GROUP_BYTES);
        bytesProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        bytesProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        bytesConsumerConn = new KafkaConsumer<byte[], byte[]>(bytesProps);
    }

    @AfterClass
    public static void after() {
        stringsConsumerConn.close();
        bytesConsumerConn.close();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startStrings").to(toStrings);

                from("direct:startBytes").to(toBytes);
            }
        };
    }

    @Test
    public void producedStringMessageIsReceivedByKafka() throws InterruptedException, IOException {
        int messageInTopic = 10;
        int messageInOtherTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic + messageInOtherTopic);

        sendMessagesInRoute(messageInTopic, stringsTemplate, "IT test message", KafkaConstants.PARTITION_KEY, "1");
        sendMessagesInRoute(messageInOtherTopic, stringsTemplate, "IT test message in other topic", KafkaConstants.PARTITION_KEY, "1", KafkaConstants.TOPIC, TOPIC_STRINGS_IN_HEADER);

        createKafkaMessageConsumer(stringsConsumerConn, TOPIC_STRINGS, TOPIC_STRINGS_IN_HEADER, messagesLatch);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue("Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount(), allMessagesReceived);
    }

    
    @Test
    public void producedStringCollectionMessageIsReceivedByKafka() throws InterruptedException, IOException {
        int messageInTopic = 10;
        int messageInOtherTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic + messageInOtherTopic);

        List<String> msgs = new ArrayList<String>();
        for (int x = 0; x < messageInTopic; x++) {
            msgs.add("Message " + x);
        }
        
        sendMessagesInRoute(1, stringsTemplate, msgs, KafkaConstants.PARTITION_KEY, "1");
        msgs = new ArrayList<String>();
        for (int x = 0; x < messageInOtherTopic; x++) {
            msgs.add("Other Message " + x);
        }
        sendMessagesInRoute(1, stringsTemplate, msgs, KafkaConstants.PARTITION_KEY, "1", KafkaConstants.TOPIC, TOPIC_STRINGS_IN_HEADER);

        createKafkaMessageConsumer(stringsConsumerConn, TOPIC_STRINGS, TOPIC_STRINGS_IN_HEADER, messagesLatch);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue("Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount(), allMessagesReceived);
    }
    
    @Test
    public void producedBytesMessageIsReceivedByKafka() throws InterruptedException, IOException {
        int messageInTopic = 10;
        int messageInOtherTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic + messageInOtherTopic);

        Map<String, Object> inTopicHeaders = new HashMap<String, Object>();
        inTopicHeaders.put(KafkaConstants.PARTITION_KEY, "1".getBytes());
        sendMessagesInRoute(messageInTopic, bytesTemplate, "IT test message".getBytes(), inTopicHeaders);

        Map<String, Object> otherTopicHeaders = new HashMap<String, Object>();
        otherTopicHeaders.put(KafkaConstants.PARTITION_KEY, "1".getBytes());
        otherTopicHeaders.put(KafkaConstants.TOPIC, TOPIC_BYTES_IN_HEADER);
        sendMessagesInRoute(messageInOtherTopic, bytesTemplate, "IT test message in other topic".getBytes(), otherTopicHeaders);

        createKafkaBytesMessageConsumer(bytesConsumerConn, TOPIC_BYTES, TOPIC_BYTES_IN_HEADER, messagesLatch);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue("Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount(), allMessagesReceived);
    }

    private void createKafkaMessageConsumer(KafkaConsumer<String, String> consumerConn,
                                            String topic, String topicInHeader, CountDownLatch messagesLatch) {

        consumerConn.subscribe(Arrays.asList(topic, topicInHeader));
        boolean run = true;

        while (run) {
            ConsumerRecords<String, String> records = consumerConn.poll(100);
            for (ConsumerRecord<String, String> record : records) {
                messagesLatch.countDown();
                if (messagesLatch.getCount() == 0) {
                    run = false;
                }
            }
        }

    }

    private void createKafkaBytesMessageConsumer(KafkaConsumer<byte[], byte[]> consumerConn, String topic,
                                                 String topicInHeader, CountDownLatch messagesLatch) {

        consumerConn.subscribe(Arrays.asList(topic, topicInHeader));
        boolean run = true;

        while (run) {
            ConsumerRecords<byte[], byte[]> records = consumerConn.poll(100);
            for (ConsumerRecord<byte[], byte[]> record : records) {
                messagesLatch.countDown();
                if (messagesLatch.getCount() == 0) {
                    run = false;
                }
            }
        }

    }

    private void sendMessagesInRoute(int messages, ProducerTemplate template, Object bodyOther, String... headersWithValue) {
        Map<String, Object> headerMap = new HashMap<String, Object>();
        for (int i = 0; i < headersWithValue.length; i = i + 2) {
            headerMap.put(headersWithValue[i], headersWithValue[i + 1]);
        }
        sendMessagesInRoute(messages, template, bodyOther, headerMap);
    }

    private void sendMessagesInRoute(int messages, ProducerTemplate template, Object bodyOther, Map<String, Object> headerMap) {
        for (int k = 0; k < messages; k++) {
            template.sendBodyAndHeaders(bodyOther, headerMap);
        }
    }

}
