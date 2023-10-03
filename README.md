# Akka Streams / Alpakka Azure Event Hubs - Java Samples

This repository provides Java based Akka Streams examples of using the Alpakka Azure Event Hubs connector.

> Note: The Alpakka Azure Event Hubs connector is only available to specific licensed Lightbend customers.

## Overview

The Akka Stream samples contained in this repo are based upon the Protobuf based events called UserPurchaseProto. Java classes are automatically generated by the Akka gRPC plugin described [here](https://doc.akka.io/docs/akka-grpc/current/).

Samples Sources, Flows, and Sinks are provided in two classes:  

1. [EventHubProducerFlows](./src/main/java/com/lightbend/streams/EventHubsProducerFlows.java)
2. [EventHubConsumerFlows](./src/main/java/com/lightbend/streams/EventHubsConsumerFlows.java)

Simple sample applications have been provided in the app package which ultimately leverage each of the sample Akka Streams.  

## Set up / Configuration

Before running any of the sample applications you'll need to provide environment variables to enable proper connectivity to Azure Event Hubs. All environment variables are picked up through the `application.conf` file.

>**Note**: our application samples are producing to, and consuming from the same Event Hub (topic). In the use case that your application needs to consume from one Event Hub and produce to another then you'll need to take care to create separate versions of the `eventhubs` configuration for producers and consumers and load appropriately.

### Event Hubs Connectivity

EVENT_HUBS_CONNECTION_STRING - The connection string for the Event Hub.
For more information on creating connection strings please [this](https://learn.microsoft.com/en-us/azure/event-hubs/event-hubs-java-get-started-send?tabs=connection-string%2Croles-azure-portal#get-the-connection-string).

EVENT_HUBS_HUB_NAME - This is synonymous with a Kafka topic name.

EVENT_HUBS_CONSUMER_GROUP - This setting allows one or more applications to consume elements of a stream at their own rate. For more information, please see [this](https://learn.microsoft.com/en-us/azure/event-hubs/event-hubs-features#consumer-groups).

### Consumer Checkpoint Store (Blob Storage) Settings

CHECKPOINT_STORE_CONNECTION_STRING - The checkpoint store is used to save position of the last element read. This is important when restarting a consumer so that the application can pick up where it left off.

CHECKPOINT_STORE_CONTAINER_NAME - container name used for the unique checkpoint store.

CHECKPOINT_STORE_SAS_TOKEN - Shared Access Token. For more information, please see [this](https://learn.microsoft.com/en-us/azure/event-hubs/event-hubs-features#sas-tokens).
