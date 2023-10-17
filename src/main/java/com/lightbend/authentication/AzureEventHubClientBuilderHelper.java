package com.lightbend.authentication;

import com.azure.identity.ClientSecretCredential;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.typesafe.config.Config;

public class AzureEventHubClientBuilderHelper {

    /*
    Note: the connection string here is a fully qualified domain name (FQDN),
        this is not the whole string that you can generate within Azure
    a typeical example would be like
    final String connectionString = "\"" + "eh-akka.servicebus.windows.net" + "\""
     */

    // for local Event Hubs connection
    public static EventProcessorClientBuilder getEventProcessorClientUsingClientSecret(Config config) {
        final ClientSecretCredential credentials = AzureClientCredentialBuilderHelper.getClientCredential(config);
        return new EventProcessorClientBuilder()
                .fullyQualifiedNamespace(config.getString("namespace"))
                .eventHubName(config.getString("eventHubName"))
                .consumerGroup(config.getString("consumer-group"))
                .credential(credentials);
    }

    // for AKS Event Hubs connection through the proxy
    public static EventProcessorClientBuilder getEventProcessorClientViaProxy(Config config) {
        final ClientSecretCredential credentials = AzureClientCredentialBuilderHelper.getClientCredentialUsingProxy(config);
        return new EventProcessorClientBuilder()
                .fullyQualifiedNamespace(config.getString("namespace"))
                .eventHubName(config.getString("eventHubName"))
                .consumerGroup(config.getString("consumer-group"))
                .credential(credentials);
    }


}
