package com.lightbend.streams;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.stream.alpakka.azure.eventhubs.ClientFromConfig;
import akka.stream.alpakka.azure.eventhubs.ProducerMessage;
import akka.stream.alpakka.azure.eventhubs.javadsl.Producer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.Flow;
import akka.stream.alpakka.azure.eventhubs.javadsl.ProducerSettings;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.typesafe.config.Config;

import com.lightbend.serialization.UserPurchaseProto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

public class UserEventSingleProducerApp {
    private static final Logger log = LoggerFactory.getLogger(UserEventSingleProducerApp.class);

    private UserEventSingleProducerApp() {}

    public static Behavior<NotUsed> createGuardian() {
        return Behaviors.setup(context -> new UserEventSingleProducerApp().init());
    }

    private Behavior<NotUsed> init() {
        return Behaviors.setup(context -> {

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

            // Event Hubs Producer Sink
            Sink<ProducerMessage.Envelope<NotUsed>, CompletionStage<Done>> producerSink =
                    Flow.<ProducerMessage.Envelope<NotUsed>>create()
                            .via(Producer.flow(producerSettings, producerClient))
                            .toMat(Sink.ignore(),Keep.right());

            // Primary Stream
            CompletionStage<Done> done =
                    Source.repeat(NotUsed.getInstance())
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

                            })
                            .map(purchase -> {
                                EventData eventData = new EventData(purchase.toByteArray());
                                return ProducerMessage.singleWithPartitioning(eventData, ProducerMessage.partitionByKey(purchase.getUserId()));
                            })
                            .runWith(producerSink.async(), context.getSystem());

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
