package com.lightbend.authentication;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.*;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.typesafe.config.Config;

public class AzureEHProducerBuilderHelper {

    public static EventHubProducerAsyncClient getEHProducerDefaultAsyncClient(Config config) {
        TokenCredential credential = new DefaultAzureCredentialBuilder()
                .build();

        // "<<fully-qualified-namespace>>" will look similar to "{your-namespace}.servicebus.windows.net"
        // "<<event-hub-name>>" will be the name of the Event Hub instance you created inside the Event Hubs namespace.
        EventHubProducerAsyncClient producer = new EventHubClientBuilder()
                .credential(
                        config.getString("namespace"),
                        config.getString("eventHubName"),
                        credential
                )
                .buildAsyncProducerClient();
        return producer;
    }

    // by service principal
    public static EventHubProducerAsyncClient getEHProducerServicePrincipalAsyncClient(Config config) {
        final ClientSecretCredential credentials = AzureClientCredentialBuilderHelper.getClientCredential(config);
        EventHubProducerAsyncClient producer = new EventHubClientBuilder()
                .credential(
                        config.getString("namespace"),
                        config.getString("eventHubName"),
                        credentials)
                .buildAsyncProducerClient();
        return producer;
    }

    // by managed identity
}
