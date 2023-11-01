package com.lightbend.apps;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.stream.alpakka.azure.eventhubs.ClientFromConfig;
import akka.stream.alpakka.azure.eventhubs.javadsl.ProducerSettings;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.lightbend.authentication.AzureEHProducerBuilderHelper;
import com.lightbend.streams.EventHubsProducerFlows;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

public class UserPurchaseSingleProducerApp {
    private static final Logger log = LoggerFactory.getLogger(UserPurchaseSingleProducerApp.class);

    private UserPurchaseSingleProducerApp() {}

    public static Behavior<NotUsed> createGuardian() {
        return Behaviors.setup(context -> new UserPurchaseSingleProducerApp().init());
    }

    private Behavior<NotUsed> init() {
        return Behaviors.setup(context -> {
            Config config = context.getSystem().settings().config();
            int batchedTimeWindowSeconds = config.getInt("app.batched-producer-time-window-seconds");
            int numPartitions = config.getInt("app.number-of-partitions");

            // Get Configurations - merge with reference.conf default settings
            Config producerConfig = config.getConfig("eventhubs-client")
                    .withFallback(config.getConfig("alpakka.azure.eventhubs"));

//            Config producerConfig = config.getConfig("eventhubs-client.producer");

            // Evemt Hubs Configuration
            ProducerSettings producerSettings = ProducerSettings.create(producerConfig);
            EventHubProducerAsyncClient producerClient = ClientFromConfig.producer(producerConfig.getConfig("eventhub"));
//            EventHubProducerAsyncClient producerClient = AzureEHProducerBuilderHelper.getEHProducerDefaultAsyncClient(producerConfig);
//            EventHubProducerAsyncClient producerClient = AzureEHProducerBuilderHelper.getEHProducerServicePrincipalAsyncClient(producerConfig);
            EventHubsProducerFlows eventHubsProducerFlows = EventHubsProducerFlows.create(producerSettings, producerClient, batchedTimeWindowSeconds, numPartitions);

            // Primary Stream
            CompletionStage<Done> done =
                    eventHubsProducerFlows.getUserEventSource()
                            .via(eventHubsProducerFlows.getSinglePartitionFlow())
                            .runWith(eventHubsProducerFlows.createProducerSink().async(), context.getSystem());

            // tear down
            done.thenRun (() -> context.getSystem().terminate());
            return Behaviors.empty();
        });
    }

    public static void main(String[] args) {
        ActorSystem<NotUsed> system = ActorSystem.create(createGuardian(), "UserEventSingleProducerApp");
        system.getWhenTerminated();
    }
}
