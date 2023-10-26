package com.lightbend.streams;

import akka.NotUsed;
import akka.stream.javadsl.Flow;
import com.lightbend.models.CustomElementWrapper;
import com.lightbend.serialization.UserPurchaseProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class LogThrottleFlows {
    private static final Logger log = LoggerFactory.getLogger(LogThrottleFlows.class);

    private LogThrottleFlows() {}

    public static LogThrottleFlows create() {
        return new LogThrottleFlows();
    }

    public Flow<UserPurchaseProto, UserPurchaseProto, NotUsed> getThrottledAndLogEachUserPurchaseProto() {
        return Flow.<UserPurchaseProto>create()
                .log("UserPurchaseProto")
                .throttle(1, Duration.ofSeconds(1));
    }

    public Flow<CustomElementWrapper, CustomElementWrapper, NotUsed> getThrottledAndLogEachCustomElementWrapper() {
        return Flow.<CustomElementWrapper>create()
                .log("CustomElementWrapper")
                .throttle(1, Duration.ofSeconds(1));
    }
}
