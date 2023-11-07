package com.lightbend.authentication;

import com.azure.identity.ClientSecretCredential;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.typesafe.config.Config;

public class AzureEHBlobStoreClientBuilderHelper {

    public static BlobContainerAsyncClient getAsyncClientViaConnectionString(Config config) {
        return new BlobContainerClientBuilder()
            .connectionString(config.getString("blob-storage.connection-string"))
            .containerName(config.getString("blob-storage.container-name"))
            .buildAsyncClient();
    }

    public static BlobContainerAsyncClient getServicePrincipalAsyncClient(Config config) {
        final ClientSecretCredential credentials = AzureClientCredentialBuilderHelper.getClientCredential(config.getConfig("blob-storage"));
        return new BlobContainerClientBuilder()
                .credential(credentials)
                .endpoint(config.getString("blob-storage.endpoint"))
                .containerName(config.getString("blob-storage.container-name"))
                .buildAsyncClient();
    }

}
