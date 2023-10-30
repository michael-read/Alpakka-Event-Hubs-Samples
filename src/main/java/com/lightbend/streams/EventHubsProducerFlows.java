package com.lightbend.streams;

import akka.Done;
import akka.NotUsed;
import akka.stream.*;
import akka.stream.alpakka.azure.eventhubs.ProducerMessage;
import akka.stream.alpakka.azure.eventhubs.javadsl.Producer;
import akka.stream.alpakka.azure.eventhubs.javadsl.ProducerSettings;
import akka.stream.javadsl.*;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.lightbend.serialization.UserPurchaseProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class EventHubsProducerFlows {

    private static final Logger log = LoggerFactory.getLogger(EventHubsProducerFlows.class);

    private final int MEGA_BYTE = 1024*1024;
    private final int PER_ELEMENT_OVERHEAD = 16; // bytes of overhead per element

    private final ProducerSettings producerSettings;
    private final EventHubProducerAsyncClient producerClient;
    private final int batchedTimeWindowSeconds;
    private final int numPartitions;

    private EventHubsProducerFlows(
            ProducerSettings producerSettings,
            EventHubProducerAsyncClient producerClient,
            int batchedTimeWindowSeconds,
            int numPartitions
    ) {
        this.producerSettings = producerSettings;
        this.producerClient = producerClient;
        this.batchedTimeWindowSeconds = batchedTimeWindowSeconds;
        this.numPartitions = numPartitions;
    }

    static public EventHubsProducerFlows create(
            ProducerSettings producerSettings,
            EventHubProducerAsyncClient producerClient,
            int batchedTimeWindowSeconds,
            int numPartitions
    ) {
        return new EventHubsProducerFlows(producerSettings, producerClient, batchedTimeWindowSeconds, numPartitions);
    }

    /*
    getUserEventSource returns a never ending source of random UserPurchaseProto events
     */
    public Source<UserPurchaseProto, NotUsed> getUserEventSource() {
        int nrUsers = 4000;
        int maxPrice = 10000;
        int maxQuantity = 5;

        List<String> products = new ArrayList<>();
        products.add("kalix t-shirt");
        products.add("akka t-shirt");
        products.add("scala t-shirt");
        products.add("skis");
        products.add("climbing shoes");
        products.add("rope");

        Random random = new Random();
        AtomicLong counter = new AtomicLong(0L);

//        return Source.repeat(NotUsed.getInstance())   // creates a never ending stream
        return Source.range(1, 100)
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
    }

    /*
    createProducerSink commits producer envelopes that have been sent to the connector
     */
    public Sink<ProducerMessage.Envelope<NotUsed>, CompletionStage<Done>> createProducerSink() {
        return Flow.<ProducerMessage.Envelope<NotUsed>>create()
                .via(Producer.flow(producerSettings, producerClient))
                .toMat(Sink.ignore(), Keep.right());
    }

    /*
    createPartitionedBatchSink collects event into a batch and emits to the connector when the size or time window has been reached.
     */
    Sink<UserPurchaseProto, CompletionStage<Done>> createPartitionedBatchSink(String partition) {
        return Flow.<UserPurchaseProto>create()
            .groupedWeightedWithin(MEGA_BYTE, e -> (long) e.toByteArray().length + PER_ELEMENT_OVERHEAD, Duration.ofSeconds(batchedTimeWindowSeconds))
            .mapConcat(eList -> eList.stream()
                    .collect(Collectors.groupingBy(UserPurchaseProto::getUserId))
                    .entrySet())
            .map(entrySet -> {
                List<EventData> events = entrySet.getValue().stream().map(e -> new EventData(e.toByteArray())).toList();
                return ProducerMessage.batchWithPartitioning(events, ProducerMessage.explicitPartitioning(partition));
            })
            .via(Producer.flow(producerSettings, producerClient))
            .toMat(Sink.ignore(),Keep.right());
    }

    /*
    getSinglePartitionFlow converts UserPurchaseProto data into EventData and places into a single Producer Envelope.
     */
    public Flow<UserPurchaseProto, ProducerMessage.Envelope<NotUsed>, NotUsed> getSinglePartitionFlow() {
        return Flow.<UserPurchaseProto>create()
                .map(purchase -> {
                    EventData eventData = new EventData(purchase.toByteArray());
//                    return ProducerMessage.singleWithPartitioning(eventData, ProducerMessage.partitionByKey(purchase.getUserId()));
                    return ProducerMessage.single(eventData);
                });
    }

    /*
    createSinglePartitionBatchedFlow collects events into a batch for a single partition and emits downstream when the size or time window has been reached.
     */
    public Flow<UserPurchaseProto, ProducerMessage.Envelope<NotUsed>, NotUsed> createSinglePartitionBatchedFlow() {
        return Flow.<UserPurchaseProto>create()
                .groupedWeightedWithin(MEGA_BYTE, e -> (long) e.toByteArray().length + PER_ELEMENT_OVERHEAD, Duration.ofSeconds(batchedTimeWindowSeconds))
                .mapConcat(eList -> eList.stream()
                        .collect(Collectors.groupingBy(UserPurchaseProto::getUserId))
                        .entrySet()
                )
                .map(entrySet -> {
                    List<EventData> events = entrySet.getValue().stream().map(e -> new EventData(e.toByteArray())).toList();
                    return ProducerMessage.batch(events);
/*
                                For Round Robin partitioning you could use the following instead of the pain .batch(events) above:

                                return ProducerMessage.batchWithPartitioning(events, ProducerMessage.roundRobinPartitioning());

                                However, it worth noting that if there's a need to maintain message ordering this is probably not the way to go.
*/
                });
    }

    /*
    createPartitionedBatchFlow collects events into a batch for a specific partition and emits downstream when the size or time window has been reached.
     */
    public Flow<UserPurchaseProto, ProducerMessage.Envelope<NotUsed>, NotUsed> createPartitionedBatchFlow(String partition) {
        return Flow.<UserPurchaseProto>create()
                .groupedWeightedWithin(MEGA_BYTE, e -> (long) e.toByteArray().length + PER_ELEMENT_OVERHEAD, Duration.ofSeconds(batchedTimeWindowSeconds))
                .mapConcat(eList -> eList.stream()
                        .collect(Collectors.groupingBy(UserPurchaseProto::getUserId))
                        .entrySet())
                .map(entrySet -> {
                    List<EventData> events = entrySet.getValue().stream().map(e -> new EventData(e.toByteArray())).toList();
                    return ProducerMessage.batchWithPartitioning(events, ProducerMessage.explicitPartitioning(partition));
                });
    }

    /*
    getPartitionedBatchedGraph returns a graph that fans out and collects batches by partition, and then fans back into and single sink
     */
    public Graph<ClosedShape, CompletionStage<Done>> getPartitionedBatchedGraph(Source<UserPurchaseProto, NotUsed> source) {
         return GraphDSL.create(
                createProducerSink(),
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

                    // set up initial flow from source to sink for the 1st partition (0)
                    builder
                            .from(graphSource)
                            .viaFanOut(partitions)
                            .via(builder.add(this.createPartitionedBatchFlow(String.valueOf(0))))
                            .viaFanIn(merge)
                            .to(out); // to() expects a SinkShape

                    // set up the flows for the remaining partitions
                    for (int i = 1; i < numPartitions; i++) {
                        builder.from(partitions).via(builder.add(this.createPartitionedBatchFlow(String.valueOf(i)))).toFanIn(merge);
                    }

                    return ClosedShape.getInstance();
                }
        );
    }

    /*
    getPartitionedBatchedSinkedGraph returns a graph that fans out and collects batches by partition, and finally emits to sink per partition
     */
    public Graph<ClosedShape, NotUsed> getPartitionedBatchedSinkedGraph(Source<UserPurchaseProto, NotUsed> source) {
        return GraphDSL.create(
                builder -> {
                    final UniformFanOutShape<UserPurchaseProto, UserPurchaseProto> partition =
                            builder.add(
                                    Partition.create(
                                            UserPurchaseProto.class, numPartitions, userPurchase -> (Math.abs(userPurchase.getUserId().hashCode()) % numPartitions)));
                    builder.from(builder.add(source)).viaFanOut(partition);
                    for (int i = 0; i < numPartitions; i++) {
                        builder.from(partition.out(i)).to(builder.add(createPartitionedBatchSink(String.valueOf(i)).async()));
                    }
                    return ClosedShape.getInstance();
                });
    }

}
