package com.lightbend.apps;

import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.stream.alpakka.azure.eventhubs.javadsl.ProducerSettings;
import akka.stream.javadsl.RunnableGraph;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.lightbend.authentication.AzureEHProducerBuilderHelper;
import com.lightbend.streams.EventHubsProducerFlows;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserPurchasePartitionedBatchProducerSinksApp {
    private static final Logger log = LoggerFactory.getLogger(UserPurchasePartitionedBatchProducerSinksApp.class);

    private UserPurchasePartitionedBatchProducerSinksApp() {}

    public static Behavior<NotUsed> createGuardian() {
        return Behaviors.setup(context -> new UserPurchasePartitionedBatchProducerSinksApp().init());
    }

    private Behavior<NotUsed> init() {
        return Behaviors.setup(context -> {

            Config config = context.getSystem().settings().config();
            int batchedTimeWindowSeconds = config.getInt("app.batched-producer-time-window-seconds");
            int numPartitions = config.getInt("app.number-of-partitions");

            // Event Hubs Producer Configuration
            ProducerSettings producerSettings = ProducerSettings.create(context.getSystem());
//            EventHubProducerAsyncClient producerClient = ClientFromConfig.producer(config.getConfig("alpakka.azure.eventhubs.eventhub"));
            EventHubProducerAsyncClient producerClient = AzureEHProducerBuilderHelper.getEHProducerDefaultAsyncClient(config.getConfig("alpakka.azure.eventhubs"));
//            EventHubProducerAsyncClient producerClient = AzureEHProducerBuilderHelper.getEHProducerServicePrincipalAsyncClient(config.getConfig("alpakka.azure.eventhubs"));
            EventHubsProducerFlows eventHubsProducerFlows = EventHubsProducerFlows.create(producerSettings, producerClient, batchedTimeWindowSeconds, numPartitions);

            final RunnableGraph<NotUsed> runnableGraph =
                    RunnableGraph.fromGraph(eventHubsProducerFlows.getPartitionedBatchedSinkedGraph(eventHubsProducerFlows.getUserEventSource()));

            return Behaviors.empty();
        });
    }


    public static void main(String[] args) {
        ActorSystem<NotUsed> system = ActorSystem.create(createGuardian(), "UserEventPartitionedBatchProducerApp");
        system.getWhenTerminated();
    }
}
