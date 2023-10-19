package com.lightbend.authentication;

import com.azure.core.http.HttpClient;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.okhttp.OkHttpAsyncHttpClientBuilder;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.typesafe.config.Config;

import java.net.InetSocketAddress;

public class AzureClientCredentialBuilderHelper {

    public static ClientSecretCredential getClientCredential(Config config) {
        return new ClientSecretCredentialBuilder()
                .clientId(config.getString("clientId"))
                .clientSecret(config.getString("clientSecret"))
                .tenantId(config.getString("tenantId"))
                .build();
    }

    public static HttpClient getHttpClient(Config config) {
        final ProxyOptions proxyOptions = new ProxyOptions(ProxyOptions.Type.HTTP,
                new InetSocketAddress(config.getString("hostName"), config.getInt("port")));
        return new OkHttpAsyncHttpClientBuilder()
                .proxy(proxyOptions)
                .build();
    }

    public static ClientSecretCredential getClientCredentialUsingProxy(Config config) {
        return new ClientSecretCredentialBuilder()
                .clientId(config.getString("clientId"))
                .clientSecret(config.getString("clientSecret"))
                .tenantId(config.getString("tenantId"))
                .httpClient(getHttpClient(config))
                .build();
    }


}
