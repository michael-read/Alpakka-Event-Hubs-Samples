package com.lightbend.apps;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.stream.alpakka.azure.eventhubs.ClientFromConfig;
import akka.stream.alpakka.azure.eventhubs.javadsl.ProducerSettings;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
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
            int batchedTimeWindowSeconds = context.getSystem().settings().config().getInt("app.batched-producer-time-window-seconds");
            int numPartitions = context.getSystem().settings().config().getInt("app.number-of-partitions");

            // Evemt Hubs Configuration
            Config eventHubConfig = context.getSystem().settings().config().getConfig("event-hub-sample");

            ProducerSettings producerSettings = ProducerSettings.create(eventHubConfig);
            EventHubProducerAsyncClient producerClient = ClientFromConfig.producer(eventHubConfig.getConfig("eventhub"));

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
