package com.lightbend.authentication;

import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.ChainedTokenCredentialBuilder;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.typesafe.config.Config;

public class AzureEHConsumerBuilderHelper {

/*    private static String getFullyQualifiedNamespace(String namespace, String eventHubName, String consumerGroup) {
        StringBuilder sb = new StringBuilder();
        sb.append(namespace);
        sb.append("/");
        sb.append(eventHubName);
        sb.append("/");
        sb.append(consumerGroup);
        return sb.toString();
    }*/

    // for local Event Hubs connection
    public static EventProcessorClientBuilder getEventProcessorClientServicePrincipal(Config config) {
        final ClientSecretCredential credentials = AzureClientCredentialBuilderHelper.getClientCredential(config.getConfig("consumer"));
        return new EventProcessorClientBuilder()
                .fullyQualifiedNamespace(
                        config.getString("consumer.namespace")
/*                        getFullyQualifiedNamespace(
                            config.getString("namespace"),
                            config.getString("hub-name"),
                            config.getString("consumer-group"))*/

                )
                .eventHubName(config.getString("consumer.hub-name"))
                .credential(credentials);
    }

    // for AKS Event Hubs connection through the proxy
    public static EventProcessorClientBuilder getEventProcessorClientViaProxy(Config config) {
        final ClientSecretCredential credentials = AzureClientCredentialBuilderHelper.getClientCredential(config.getConfig("consumer"));
        return new EventProcessorClientBuilder()
                .fullyQualifiedNamespace(
                        config.getString("consumer.namespace")
/*                        getFullyQualifiedNamespace(
                                config.getString("namespace"),
                                config.getString("hub-name"),
                                config.getString("consumer-group"))*/
                )
                .eventHubName(config.getString("consumer.hub-name"))
                .credential(credentials);

    }

    public static EventProcessorClientBuilder getClientManagedIdentity(Config config) {
        return new EventProcessorClientBuilder()
                .fullyQualifiedNamespace(
                        config.getString("consumer.namespace")
/*                        getFullyQualifiedNamespace(
                                config.getString("namespace"),
                                config.getString("hub-name"),
                                config.getString("consumer-group"))*/
                )
                .eventHubName(config.getString("consumer.hub-name"))
                .credential(new ChainedTokenCredentialBuilder()
                        .addFirst(new ManagedIdentityCredentialBuilder().build())
                        .addLast(new AzureCliCredentialBuilder().build())
                        .build()
                );
    }

}
