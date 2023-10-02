package com.lightbend.apps;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.japi.Pair;
import akka.stream.alpakka.azure.eventhubs.ClientFromConfig;
import akka.stream.alpakka.azure.eventhubs.javadsl.CheckpointSettings;
import akka.stream.alpakka.azure.eventhubs.javadsl.Checkpointer;
import akka.stream.alpakka.azure.eventhubs.javadsl.Consumer;
import akka.stream.alpakka.azure.eventhubs.javadsl.ConsumerSettings;
import akka.stream.javadsl.Keep;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.lightbend.streams.EventHubsConsumerFlows;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

public class UserPurchaseConsumerWithContextApp {
    private static final Logger log = LoggerFactory.getLogger(UserPurchaseConsumerWithContextApp.class);

    private UserPurchaseConsumerWithContextApp() {}

    public static Behavior<NotUsed> createGuardian() {
        return Behaviors.setup(context -> new UserPurchaseConsumerWithContextApp().init());
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

            EventHubsConsumerFlows eventHubsConsumerFlows = EventHubsConsumerFlows.create(
                    consumerSettings,
                    checkpointSettings,
                    sdkClientBuilder,
                    storageConnectionString,
                    storageContainerName,
                    sasToken
            );

            Pair<Consumer.Control, CompletionStage<Done>> result =
                    eventHubsConsumerFlows.getConsumerSourceWithContext()
                    .via(eventHubsConsumerFlows.getConsumerFlowWithContext())
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
