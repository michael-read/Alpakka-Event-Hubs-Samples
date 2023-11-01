package com.lightbend.apps;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.japi.Pair;
import akka.stream.alpakka.azure.eventhubs.javadsl.CheckpointSettings;
import akka.stream.alpakka.azure.eventhubs.javadsl.Checkpointer;
import akka.stream.alpakka.azure.eventhubs.javadsl.Consumer;
import akka.stream.alpakka.azure.eventhubs.javadsl.ConsumerSettings;
import akka.stream.javadsl.Keep;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.lightbend.authentication.AzureEHBlobStoreClientBuilderHelper;
import com.lightbend.authentication.AzureEHConsumerBuilderHelper;
import com.lightbend.streams.EventHubsConsumerFlows;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

public class UserPurchaseConsumerApp {
    private static final Logger log = LoggerFactory.getLogger(UserPurchaseConsumerApp.class);

    private UserPurchaseConsumerApp() {}

    public static Behavior<NotUsed> createGuardian() {
        return Behaviors.setup(context -> new UserPurchaseConsumerApp().init());
    }

    private Behavior<NotUsed> init() {
        return Behaviors.setup(context -> {

            Config config = context.getSystem().settings().config();

            // Get Configurations - merge with reference.conf default settings
            Config consumerConfig = config.getConfig("eventhubs-client")
                    .withFallback(config.getConfig("alpakka.azure.eventhubs"));

            ConsumerSettings consumerSettings = ConsumerSettings.create(consumerConfig.getConfig("consumer"));

            EventProcessorClientBuilder sdkClientBuilder = AzureEHConsumerBuilderHelper.getEventProcessorClientServicePrincipal(consumerConfig);
            CheckpointSettings checkpointSettings = CheckpointSettings.create(context.getSystem());
            BlobContainerAsyncClient blobContainerAsyncClient = AzureEHBlobStoreClientBuilderHelper.getServicePrincipalAsyncClient(consumerConfig);

            EventHubsConsumerFlows eventHubsConsumerFlows = EventHubsConsumerFlows.create(
                    consumerSettings,
                    checkpointSettings,
                    blobContainerAsyncClient,
                    sdkClientBuilder
            );

            Pair<Consumer.Control, CompletionStage<Done>> result = eventHubsConsumerFlows.getConsumerSource()
                    .via(eventHubsConsumerFlows.getConsumerFlow())
                    .toMat(Checkpointer.sink(checkpointSettings), Keep.both())
                    .run(context.getSystem());

            // tear down
            result.second().thenRun (() -> context.getSystem().terminate());

            // note: use the Consumer.Control (result.first()).drainAndShutdown with coordinated shutdown to properly drain the stream.

            return Behaviors.empty();
        });
    }

    public static void main(String[] args) {
        ActorSystem<NotUsed> system = ActorSystem.create(createGuardian(), "UserEventConsumerApp");
        system.getWhenTerminated();
    }
}
