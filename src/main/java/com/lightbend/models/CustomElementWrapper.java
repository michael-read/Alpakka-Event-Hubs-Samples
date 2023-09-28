package com.lightbend.models;

import akka.stream.alpakka.azure.eventhubs.Checkpointable;
import com.lightbend.serialization.UserPurchaseProto;

public record CustomElementWrapper(UserPurchaseProto userPurchaseProto, Checkpointable checkpointable) {}