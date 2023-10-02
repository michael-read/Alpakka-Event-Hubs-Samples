package com.lightbend.streams;

import akka.NotUsed;
import akka.stream.alpakka.azure.eventhubs.Checkpointable;
import akka.stream.alpakka.azure.eventhubs.javadsl.CheckpointSettings;
import akka.stream.alpakka.azure.eventhubs.javadsl.Consumer;
import akka.stream.alpakka.azure.eventhubs.javadsl.ConsumerSettings;
import akka.stream.alpakka.azure.eventhubs.javadsl.ProducerSettings;
import akka.stream.javadsl.*;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.lightbend.models.CustomElementWrapper;
import com.lightbend.serialization.UserPurchaseProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public class EventHubsConsumerFlows {

    private static final Logger log = LoggerFactory.getLogger(EventHubsConsumerFlows.class);

    private final ConsumerSettings consumerSettings;
    private final CheckpointSettings checkpointSettings;
    private final EventProcessorClientBuilder sdkClientBuilder;
    private final String storageConnectionString;
    private final String storageContainerName;
    private final String sasToken;
    private final BlobCheckpointStore checkpointStore;

    public EventHubsConsumerFlows(
            ConsumerSettings consumerSettings,
            CheckpointSettings checkpointSettings,
            EventProcessorClientBuilder sdkClientBuilder,
            String storageConnectionString,
            String storageContainerName,
            String sasToken
    ) {

        this.consumerSettings = consumerSettings;
        this.checkpointSettings = checkpointSettings;
        this.sdkClientBuilder = sdkClientBuilder;
        this.storageConnectionString = storageConnectionString;
        this.storageContainerName = storageContainerName;
        this.sasToken = sasToken;

        // Create Event Hubs Checkpoint Store
        BlobContainerAsyncClient blobContainerClient =
                new BlobContainerClientBuilder()
                        .connectionString(this.storageConnectionString)
                        .containerName(this.storageContainerName)
                        .sasToken(this.sasToken)
                        .buildAsyncClient();
        this.checkpointStore = new BlobCheckpointStore(blobContainerClient);
    }

    static public EventHubsConsumerFlows create(
            ConsumerSettings consumerSettings,
            CheckpointSettings checkpointSettings,
            EventProcessorClientBuilder sdkClientBuilder,
            String storageConnectionString,
            String storageContainerName,
            String sasToken
    ) {
        return new EventHubsConsumerFlows(
                consumerSettings,
                checkpointSettings,
                sdkClientBuilder,
                storageConnectionString,
                storageContainerName,
                sasToken
        );
    }

    public Source<CustomElementWrapper, Consumer.Control> getConsumerSource() {
        return Consumer.source(consumerSettings, sdkClientBuilder, checkpointSettings, checkpointStore, (eventData, checkpoint) -> {
            UserPurchaseProto userPurchase = UserPurchaseProto.parseFrom(eventData.getBody());
            return new CustomElementWrapper(userPurchase, checkpoint);
        });
    }

    public Flow<CustomElementWrapper, Checkpointable, NotUsed> getConsumerFlow() {
        AtomicLong counter = new AtomicLong(0L);
        return Flow.<CustomElementWrapper>create()
                .map(element -> {
                    if (log.isDebugEnabled() && (counter.incrementAndGet() % 100000) == 0) {
                        UserPurchaseProto userPurchase = element.userPurchaseProto();
                        log.debug("received purchase evnet for user {} Product {} Qty {}, Price {}",
                                userPurchase.getUserId(), userPurchase.getProduct(), userPurchase.getQuantity(), userPurchase.getPrice());
                    }
                    // TODO: do something with the userPurchase here
                    return element.checkpointable();
                });
    }

    public SourceWithContext<EventData, Checkpointable, Consumer.Control> getConsumerSourceWithContext() {
        return Consumer.sourceWithCheckpointableContext(consumerSettings, sdkClientBuilder, checkpointSettings, checkpointStore);
    }

    public FlowWithContext<EventData, Checkpointable, EventData, Checkpointable, NotUsed> getConsumerFlowWithContext() {
        AtomicLong counter = new AtomicLong(0L);
        return FlowWithContext.<EventData, Checkpointable>create().map(element -> {
            UserPurchaseProto userPurchase = UserPurchaseProto.parseFrom(element.getBody());
            if (log.isDebugEnabled() && (counter.incrementAndGet() % 100000) == 0) {
                log.debug("received purchase event for user {} Product {} Qty {}, Price {}",
                        userPurchase.getUserId(), userPurchase.getProduct(), userPurchase.getQuantity(), userPurchase.getPrice());
            }
            // TODO: do something with the userPurchase here
            return element;
        });
    }
}
