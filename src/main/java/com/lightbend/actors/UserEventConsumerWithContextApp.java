package com.lightbend.actors;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.japi.Pair;
import akka.stream.alpakka.azure.eventhubs.Checkpointable;
import akka.stream.alpakka.azure.eventhubs.ClientFromConfig;
import akka.stream.alpakka.azure.eventhubs.javadsl.CheckpointSettings;
import akka.stream.alpakka.azure.eventhubs.javadsl.Checkpointer;
import akka.stream.alpakka.azure.eventhubs.javadsl.Consumer;
import akka.stream.alpakka.azure.eventhubs.javadsl.ConsumerSettings;
import akka.stream.javadsl.FlowWithContext;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.SourceWithContext;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.lightbend.serialization.UserPurchaseProto;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

public class UserEventConsumerWithContextApp {
    private static final Logger log = LoggerFactory.getLogger(UserEventConsumerWithContextApp.class);

    private UserEventConsumerWithContextApp() {}

    public static Behavior<NotUsed> createGuardian() {
        return Behaviors.setup(context -> new UserEventConsumerWithContextApp().init());
    }

    private Behavior<NotUsed> init() {
        return Behaviors.setup(context -> {

            // Event Hubs Configuration
            Config config = context.getSystem().settings().config().getConfig("event-hub-test");
            ConsumerSettings consumerSettings = ConsumerSettings.create(config);
            EventProcessorClientBuilder sdkClientBuilder = ClientFromConfig.processorClientBuilder(config.getConfig("eventhub"));
            CheckpointSettings checkpointSettings = CheckpointSettings.create(config);
            String storageConnectionString = context.getSystem().settings().config().getString("blob-storage.connection-string");
            String storageContainerName = context.getSystem().settings().config().getString("blob-storage.container-name");
            String sasToken = context.getSystem().settings().config().getString("blob-storage.sas-token");

            // Event Hubs Checkpoint Store
            BlobContainerAsyncClient blobContainerClient =
                    new BlobContainerClientBuilder()
                            .connectionString(storageConnectionString)
                            .containerName(storageContainerName)
                            .sasToken(sasToken)
                            .buildAsyncClient();
            BlobCheckpointStore checkpointStore = new BlobCheckpointStore(blobContainerClient);

            AtomicLong counter = new AtomicLong(0L);

            SourceWithContext<EventData, Checkpointable, Consumer.Control> source =
                    Consumer.sourceWithCheckpointableContext(consumerSettings, sdkClientBuilder, checkpointSettings, checkpointStore);

            FlowWithContext<EventData, Checkpointable, EventData, Checkpointable, NotUsed> flow =
                    FlowWithContext.<EventData, Checkpointable>create().map(element -> {
                        UserPurchaseProto userPurchase = UserPurchaseProto.parseFrom(element.getBody());
                        if (log.isDebugEnabled() && (counter.incrementAndGet() % 100000) == 0) {
                            log.debug("received purchase evnet for user {} Product {} Qty {}, Price {}",
                                    userPurchase.getUserId(), userPurchase.getProduct(), userPurchase.getQuantity(), userPurchase.getPrice());
                        }
                        // TODO: do something with the userPurchase here
                        return element;
                    });

            Pair<Consumer.Control, CompletionStage<Done>> result = source
                    .via(flow)
                    .toMat(Checkpointer.sinkWithCheckpointableContext(checkpointSettings), Keep.both())
                    .run(context.getSystem());

            // tear down
            result.second().thenRun (() -> context.getSystem().terminate());

            // note: use the Consumer.Control (result.first()).drainAndShutdown with coordinated shutdown to properly drain the stream.

            return Behaviors.empty();
        });
    }

    public static void main(String[] args) {
        ActorSystem<NotUsed> system = ActorSystem.create(createGuardian(), "UserEventConsumerWithContextApp");
        system.getWhenTerminated();
    }
}
