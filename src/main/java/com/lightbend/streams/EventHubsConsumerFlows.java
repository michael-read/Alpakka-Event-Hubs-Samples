package com.lightbend.streams;

import akka.NotUsed;
import akka.stream.alpakka.azure.eventhubs.Checkpointable;
import akka.stream.alpakka.azure.eventhubs.javadsl.CheckpointSettings;
import akka.stream.alpakka.azure.eventhubs.javadsl.Consumer;
import akka.stream.alpakka.azure.eventhubs.javadsl.ConsumerSettings;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.FlowWithContext;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceWithContext;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.lightbend.models.CustomElementWrapper;
import com.lightbend.serialization.UserPurchaseProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public class EventHubsConsumerFlows {

    private static final Logger log = LoggerFactory.getLogger(EventHubsConsumerFlows.class);

    private final ConsumerSettings consumerSettings;
    private final CheckpointSettings checkpointSettings;
    private BlobContainerAsyncClient blobContainerAsyncClient;
    private final EventProcessorClientBuilder sdkClientBuilder;

    private final BlobCheckpointStore checkpointStore;

    private EventHubsConsumerFlows(
            ConsumerSettings consumerSettings,
            CheckpointSettings checkpointSettings,
            BlobContainerAsyncClient blobContainerAsyncClient,
            EventProcessorClientBuilder sdkClientBuilder
    ) {

        this.consumerSettings = consumerSettings;
        this.checkpointSettings = checkpointSettings;
        this.blobContainerAsyncClient = blobContainerAsyncClient;
        this.sdkClientBuilder = sdkClientBuilder;

        // Create Event Hubs Checkpoint Store
        this.checkpointStore = new BlobCheckpointStore(blobContainerAsyncClient);
    }

    static public EventHubsConsumerFlows create(
            ConsumerSettings consumerSettings,
            CheckpointSettings checkpointSettings,
            BlobContainerAsyncClient blobContainerAsyncClient,
            EventProcessorClientBuilder sdkClientBuilder
    ) {
        return new EventHubsConsumerFlows(
                consumerSettings,
                checkpointSettings,
                blobContainerAsyncClient,
                sdkClientBuilder
        );
    }

    /*
    getConsumerSource returns a source of elements that are placed into a CustomElementWrapper which contains the original UserPurchaseProto, and the checkpoint metadata
     */
    public Source<CustomElementWrapper, Consumer.Control> getConsumerSource() {
        return Consumer.source(consumerSettings, sdkClientBuilder, checkpointSettings, checkpointStore, (eventData, checkpoint) -> {
            UserPurchaseProto userPurchase = UserPurchaseProto.parseFrom(eventData.getBody());
            return new CustomElementWrapper(userPurchase, checkpoint);
        });
    }

    /*
    getConsumerFlow provide a sample flow of event data into the original UserPurchaseProto, and returns Checkpoint metadata for the sink
     */
    public Flow<CustomElementWrapper, Checkpointable, NotUsed> getConsumerFlow() {
        AtomicLong counter = new AtomicLong(0L);
        return Flow.<CustomElementWrapper>create()
                .map(element -> {
                    if (log.isDebugEnabled() && (counter.incrementAndGet() % 100000) == 0) {
                        UserPurchaseProto userPurchase = element.userPurchaseProto();
                        log.debug("received purchase event for user {} Product {} Qty {}, Price {}",
                                userPurchase.getUserId(), userPurchase.getProduct(), userPurchase.getQuantity(), userPurchase.getPrice());
                    }
                    // TODO: do something with the userPurchase here
                    return element.checkpointable();
                });
    }

    /*
    getConsumerSourceWithContext returns a source of elements as EventData, and carries the checkpoint metadata in the stream's context
     */
    public SourceWithContext<EventData, Checkpointable, Consumer.Control> getConsumerSourceWithContext() {
        return Consumer.sourceWithCheckpointableContext(consumerSettings, sdkClientBuilder, checkpointSettings, checkpointStore);
    }

    /*
    getConsumerFlowWithContext provides a sample flow that extracts the EventData into an UserPurchaseProto which is passed downstream/
    */
    public FlowWithContext<EventData, Checkpointable, UserPurchaseProto, Checkpointable, NotUsed> getConsumerFlowWithContext() {
        AtomicLong counter = new AtomicLong(0L);
        return FlowWithContext.<EventData, Checkpointable>create().map(element -> {
            UserPurchaseProto userPurchase = UserPurchaseProto.parseFrom(element.getBody());
            if (log.isDebugEnabled() && (counter.incrementAndGet() % 100000) == 0) {
                log.debug("received purchase event for user {} Product {} Qty {}, Price {}",
                        userPurchase.getUserId(), userPurchase.getProduct(), userPurchase.getQuantity(), userPurchase.getPrice());
            }
            return userPurchase;
        });
    }
}
