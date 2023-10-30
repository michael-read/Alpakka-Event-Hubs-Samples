package com.lightbend.apps;

import akka.Done;
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

import java.util.concurrent.CompletionStage;

public class UserPurchasePartitionedBatchProducerApp {
    private static final Logger log = LoggerFactory.getLogger(UserPurchasePartitionedBatchProducerApp.class);

    private UserPurchasePartitionedBatchProducerApp() {}

    public static Behavior<NotUsed> createGuardian() {
        return Behaviors.setup(context -> new UserPurchasePartitionedBatchProducerApp().init());
    }

    private Behavior<NotUsed> init() {
        return Behaviors.setup(context -> {

            Config config = context.getSystem().settings().config();
            int batchedTimeWindowSeconds = config.getInt("app.batched-producer-time-window-seconds");
            int numPartitions = config.getInt("app.number-of-partitions");
            Config producerConfig = config.getConfig("producer-eventhubs");

            // Evemt Hubs Configuration
            ProducerSettings producerSettings = ProducerSettings.create(context.getSystem());
            EventHubProducerAsyncClient producerClient = AzureEHProducerBuilderHelper.getEHProducerDefaultAsyncClient(producerConfig);
//            EventHubProducerAsyncClient producerClient = AzureEHProducerBuilderHelper.getEHProducerServicePrincipalAsyncClient(producerConfig);
            EventHubsProducerFlows eventHubsProducerFlows = EventHubsProducerFlows.create(producerSettings, producerClient, batchedTimeWindowSeconds, numPartitions);

            final RunnableGraph<CompletionStage<Done>> runnableGraph =
                    RunnableGraph.fromGraph(eventHubsProducerFlows.getPartitionedBatchedGraph(eventHubsProducerFlows.getUserEventSource()));

            CompletionStage<Done> done  = runnableGraph.run(context.getSystem());
            done.thenRun (() -> context.getSystem().terminate());

            return Behaviors.empty();
        });
    }


    public static void main(String[] args) {
        ActorSystem<NotUsed> system = ActorSystem.create(createGuardian(), "UserEventPartitionedBatchProducerApp");
        system.getWhenTerminated();
    }
}
