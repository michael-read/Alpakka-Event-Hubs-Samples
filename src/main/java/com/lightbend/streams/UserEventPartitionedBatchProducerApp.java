package com.lightbend.streams;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.stream.ClosedShape;
import akka.stream.Outlet;
import akka.stream.UniformFanInShape;
import akka.stream.UniformFanOutShape;
import akka.stream.alpakka.azure.eventhubs.ClientFromConfig;
import akka.stream.alpakka.azure.eventhubs.ProducerMessage;
import akka.stream.alpakka.azure.eventhubs.javadsl.Producer;
import akka.stream.alpakka.azure.eventhubs.javadsl.ProducerSettings;
import akka.stream.javadsl.*;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.lightbend.serialization.UserPurchaseProto;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class UserEventPartitionedBatchProducerApp {
    private static final Logger log = LoggerFactory.getLogger(UserEventPartitionedBatchProducerApp.class);

    private final int MEGA_BYTE = 1024*1024;

    private UserEventPartitionedBatchProducerApp() {}

    public static Behavior<NotUsed> createGuardian() {
        return Behaviors.setup(context -> new UserEventPartitionedBatchProducerApp().init());
    }

    private Behavior<NotUsed> init() {
        return Behaviors.setup(context -> {

            int batchedTimeWindowSeconds = context.getSystem().settings().config().getInt("app.batched-producer-time-window-seconds");
            int numPartitions = context.getSystem().settings().config().getInt("app.number-of-partitions");

            int nrUsers = 4000;
            int maxPrice = 10000;
            int maxQuantity = 5;

            List<String> products = new ArrayList<>();
            products.add("cat t-shirt");
            products.add("akka t-shirt");
            products.add("skis");
            products.add("climbing shoes");
            products.add("rope");

            Random random = new Random();
            AtomicLong counter = new AtomicLong(0L);

            // Evemt Hubs Configuration
            Config eventHubConfig = context.getSystem().settings().config().getConfig("event-hub-sample");

            ProducerSettings producerSettings = ProducerSettings.create(eventHubConfig);
            EventHubProducerAsyncClient producerClient = ClientFromConfig.producer(eventHubConfig.getConfig("eventhub"));

            Source<UserPurchaseProto, NotUsed> source = Source.repeat(NotUsed.getInstance())
//                            .throttle(45500, Duration.ofSeconds(1))
//                            .throttle(1, Duration.ofMinutes(2))
                    .map((notUsed) -> {
                        String randomEntityId = Integer.valueOf(random.nextInt(nrUsers)).toString();
                        long price = random.nextInt(maxPrice);
                        long quantity = random.nextInt(maxQuantity);
                        String product = products.get(random.nextInt(products.size()));
                        if (log.isDebugEnabled() && (counter.incrementAndGet() % 100000) == 0) {
                            log.debug("Sending message to user {} Product {} Qty {}, Price {}", randomEntityId, product, quantity, price);
                        }
                        return UserPurchaseProto.newBuilder()
                                .setUserId(randomEntityId)
                                .setProduct(product)
                                .setQuantity(quantity)
                                .setPrice(price).build();

                    });

/*            // Primary Stream
            RunnableGraph.fromGraph(
                GraphDSL.create(
                        builder -> {
                            final UniformFanOutShape<UserPurchaseProto, UserPurchaseProto> partition =
                                    builder.add(
                                            Partition.create(
                                                    UserPurchaseProto.class, numPartitions, userPurchase -> (Math.abs(userPurchase.getUserId().hashCode()) % numPartitions)));
                            builder.from(builder.add(source)).viaFanOut(partition);
                            for (int i = 0; i < numPartitions; i++) {
                                builder.from(partition.out(i)).to(builder.add(createPartitionedSink(producerSettings, producerClient, batchedTimeWindowSeconds, String.valueOf(i))));
                            }
                            return ClosedShape.getInstance();
                        }))
                    .run(context.getSystem());*/

            final RunnableGraph<CompletionStage<Done>> runnableGraph =
                    RunnableGraph.fromGraph(
                            GraphDSL.create(
                                    createProducerSink(producerSettings, producerClient),
                                    (builder, out) -> {
                                        // create the fan-out flow
                                        final UniformFanOutShape<UserPurchaseProto, UserPurchaseProto> partitions =
                                                builder.add(
                                                        Partition.create(
                                                                UserPurchaseProto.class, numPartitions, userPurchase -> (Math.abs(userPurchase.getUserId().hashCode()) % numPartitions)
                                                        )
                                                );
                                        // create the partitions
                                        for (int i = 0; i < numPartitions; i++) {
                                            builder.from(partitions.out(i));
                                        }

                                        // wrap the source in the builder
                                        final Outlet<UserPurchaseProto> graphSource = builder.add(source).out();

                                        // set up the fan-in
                                        final UniformFanInShape<ProducerMessage.Envelope<NotUsed>, ProducerMessage.Envelope<NotUsed>> merge
                                                = builder.add(Merge.create(numPartitions));

                                        // set up inital flow from source to sink for the 1st partition (0)
                                        builder
                                                .from(graphSource)
                                                .viaFanOut(partitions)
                                                .via(builder.add(createPartitionedFlow(batchedTimeWindowSeconds, String.valueOf(0))))
                                                .viaFanIn(merge)
                                                .to(out); // to() expects a SinkShape

                                        // set up the flows for the remaining partitions
                                        for (int i = 1; i < numPartitions; i++) {
                                            builder.from(partitions).via(builder.add(createPartitionedFlow(batchedTimeWindowSeconds, String.valueOf(i)))).toFanIn(merge);
                                        }

                                        return ClosedShape.getInstance();
                                    }
                            )
                    );

            CompletionStage<Done> done  = runnableGraph.run(context.getSystem());
            done.thenRun (() -> context.getSystem().terminate());

            return Behaviors.empty();
        });
    }

    // Event Hubs Producer Sink
    Sink<ProducerMessage.Envelope<NotUsed>, CompletionStage<Done>> createProducerSink(ProducerSettings producerSettings, EventHubProducerAsyncClient producerClient) {
        return Flow.<ProducerMessage.Envelope<NotUsed>>create()
                .via(Producer.flow(producerSettings, producerClient))
                .toMat(Sink.ignore(), Keep.right());
    }

    Flow<UserPurchaseProto, ProducerMessage.Envelope<NotUsed>, NotUsed> createPartitionedFlow(int batchedTimeWindowSeconds, String partition) {
        return Flow.<UserPurchaseProto>create()
                .groupedWeightedWithin(MEGA_BYTE, e -> (long) e.toByteArray().length, Duration.ofSeconds(batchedTimeWindowSeconds))
                .mapConcat(eList -> {
                    return eList.stream()
                            .collect(Collectors.groupingBy(e -> e.getUserId()))
                            .entrySet();
                })
                .map(entrySet -> {
                    List<EventData> events = entrySet.getValue().stream().map(e -> new EventData(e.toByteArray())).toList();
                    return ProducerMessage.batchWithPartitioning(events, ProducerMessage.explicitPartitioning(partition));
                });
    }

/*
    Sink<UserPurchaseProto, CompletionStage<Done>> createPartitionedSink(ProducerSettings producerSettings, EventHubProducerAsyncClient producerClient, int batchedTimeWindowSeconds, String partition) {
        return Flow.<UserPurchaseProto>create()
            .groupedWeightedWithin(MEGA_BYTE, e -> (long) e.toByteArray().length, Duration.ofSeconds(batchedTimeWindowSeconds))
            .mapConcat(eList -> {
                return eList.stream()
                        .collect(Collectors.groupingBy(e -> e.getUserId()))
                        .entrySet();
            })
            .map(entrySet -> {
                List<EventData> events = entrySet.getValue().stream().map(e -> new EventData(e.toByteArray())).toList();
                return ProducerMessage.batchWithPartitioning(events, ProducerMessage.explicitPartitioning(partition));
            })
            .via(Producer.flow(producerSettings, producerClient))
            .toMat(Sink.ignore(),Keep.right());
    }
*/

    public static void main(String[] args) {
        ActorSystem<NotUsed> system = ActorSystem.create(createGuardian(), "UserEventPartitionedBatchProducerApp");
        system.getWhenTerminated();
    }
}
