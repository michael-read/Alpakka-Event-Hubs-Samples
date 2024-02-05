package com.lightbend.authentication;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.*;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureEHProducerBuilderHelper {
    private static final Logger log = LoggerFactory.getLogger(AzureEHProducerBuilderHelper.class);

    public static EventHubProducerAsyncClient getEHProducerDefaultAsyncClient(Config config) {
        DefaultAzureCredential defaultCredential = new DefaultAzureCredentialBuilder().build();
        // "<<fully-qualified-namespace>>" will look similar to "{your-namespace}.servicebus.windows.net"
        // "<<event-hub-name>>" will be the name of the Event Hub instance you created inside the Event Hubs namespace.
        var clientBuilder = new EventHubClientBuilder();
        if (config.getString("eventhub.connection-string").isEmpty()) {
            clientBuilder
                .credential(defaultCredential)
                .eventHubName(config.getString("eventhub.hub-name"))
                .fullyQualifiedNamespace(config.getString("producer.namespace"));
        }
        else {
            log.info("connection-string '{}'", config.getString("eventhub.connection-string"));
            clientBuilder.connectionString(config.getString("eventhub.connection-string"), config.getString("eventhub.hub-name"));
        }
        return clientBuilder.buildAsyncProducerClient();
    }

    // by service principal
    public static EventHubProducerAsyncClient getEHProducerServicePrincipalAsyncClient(Config config) {
        final ClientSecretCredential credentials = AzureClientCredentialBuilderHelper.getClientCredential(config.getConfig("producer"));
        EventHubProducerAsyncClient producer = new EventHubClientBuilder()
                .credential(credentials)
                .eventHubName(config.getString("eventhub.hub-name"))
                .fullyQualifiedNamespace(config.getString("producer.namespace"))
                .buildAsyncProducerClient();
        return producer;
    }

    // by managed identity
}
