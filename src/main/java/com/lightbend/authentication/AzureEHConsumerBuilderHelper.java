package com.lightbend.authentication;

import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.ChainedTokenCredentialBuilder;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.typesafe.config.Config;

public class AzureEHConsumerBuilderHelper {

    public static EventProcessorClientBuilder getEventProcessorViaConnectionString(Config config) {
        return new EventProcessorClientBuilder()
                .connectionString(
                        config.getString("eventhub.connection-string"),
                        config.getString("eventhub.hub-name")
                )
                .consumerGroup(config.getString("consumer.consumer-group"));
    }

    public static EventProcessorClientBuilder getEventProcessorClientServicePrincipal(Config config) {
        final ClientSecretCredential credentials = AzureClientCredentialBuilderHelper.getClientCredential(config.getConfig("consumer"));
        return new EventProcessorClientBuilder()
                .fullyQualifiedNamespace(config.getString("consumer.namespace"))
                .eventHubName(config.getString("eventhub.hub-name"))
                .credential(credentials);
    }

    // for AKS Event Hubs connection through the proxy
    public static EventProcessorClientBuilder getEventProcessorClientViaProxy(Config config) {
        final ClientSecretCredential credentials = AzureClientCredentialBuilderHelper.getClientCredential(config.getConfig("consumer"));
        return new EventProcessorClientBuilder()
                .fullyQualifiedNamespace(config.getString("consumer.namespace"))
                .eventHubName(config.getString("eventhub.hub-name"))
                .credential(credentials);

    }

    public static EventProcessorClientBuilder getClientManagedIdentity(Config config) {
        return new EventProcessorClientBuilder()
                .fullyQualifiedNamespace(config.getString("consumer.namespace"))
                .eventHubName(config.getString("eventhub.hub-name"))
                .credential(new ChainedTokenCredentialBuilder()
                        .addFirst(new ManagedIdentityCredentialBuilder().build())
                        .addLast(new AzureCliCredentialBuilder().build())
                        .build()
                );
    }

}
