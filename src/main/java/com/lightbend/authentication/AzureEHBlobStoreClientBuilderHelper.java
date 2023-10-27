package com.lightbend.authentication;

import com.azure.identity.ClientSecretCredential;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.typesafe.config.Config;

public class AzureEHBlobStoreClientBuilderHelper {

    public static BlobContainerAsyncClient getSimpleAsyncClient(Config config) {
        return new BlobContainerClientBuilder()
            .connectionString(config.getString("connection-string"))
            .containerName(config.getString("container-name"))
            .sasToken(config.getString("sas-token"))
            .buildAsyncClient();
    }

    public static BlobContainerAsyncClient getServicePrincipalAsyncClient(Config config) {
        final ClientSecretCredential credentials = AzureClientCredentialBuilderHelper.getClientCredential(config);
        return new BlobContainerClientBuilder()
                .credential(credentials)
                .endpoint(config.getString("container-url"))
                .buildAsyncClient();
    }

}
