/*******************************************************************************
 *
 *    Copyright 2019 Adobe. All rights reserved.
 *    This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License. You may obtain a copy
 *    of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software distributed under
 *    the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *    OF ANY KIND, either express or implied. See the License for the specific language
 *    governing permissions and limitations under the License.
 *
 ******************************************************************************/

package org.eclipse.microprofile.graphql.superhero_client.GraphQLClient.impl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.shopify.graphql.support.SchemaViolationError;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.eclipse.microprofile.graphql.superhero_client.GraphQLClient.GraphQLRequest;
import org.eclipse.microprofile.graphql.superhero_client.GraphQLClient.GraphQLResponse;
import org.eclipse.microprofile.graphql.superhero_client.GraphQLClient.GraphQLClient;
import org.eclipse.microprofile.graphql.superhero_client.GraphQLClient.HttpMethod;
import org.eclipse.microprofile.graphql.superhero_client.GraphQLClient.RequestOptions;
import org.eclipse.microprofile.graphql.superhero_client.SuperHeroAPI.QueryQuery;
import org.eclipse.microprofile.graphql.superhero_client.SuperHeroAPI.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GraphQLClientImpl implements GraphQLClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLClientImpl.class);

    protected HttpClient client;
    private Gson gson;

    private String url;
    private boolean acceptSelfSignedCertificates;
    private int maxHttpConnections;
    private HttpMethod httpMethod;
    private int connectionTimeout;
    private int socketTimeout;
    private int requestPoolTimeout;
    private String[] httpHeaders;

    public GraphQLClientImpl(GraphqlClientConfiguration configuration) throws Exception {
        url = configuration.url();
        acceptSelfSignedCertificates = configuration.acceptSelfSignedCertificates();
        maxHttpConnections = configuration.maxHttpConnections();
        httpMethod = configuration.httpMethod();
        connectionTimeout = configuration.connectionTimeout();
        socketTimeout = configuration.socketTimeout();
        requestPoolTimeout = configuration.requestPoolTimeout();
        httpHeaders = configuration.httpHeaders();

        client = buildHttpClient();
        gson = new Gson();
    }

    @Override
    public QueryResponse execute(QueryQuery query) throws SchemaViolationError {
        LOGGER.debug("Executing GraphQL query: " + query.toString());

        GraphQLRequest request = new GraphQLRequest(query.toString());
        HttpResponse httpResponse;
        try {
            httpResponse = client.execute(buildRequest(request, null));
        } catch (Exception e) {
            throw new RuntimeException("Failed to send GraphQL request", e);
        }

        StatusLine statusLine = httpResponse.getStatusLine();
        if (HttpStatus.SC_OK == statusLine.getStatusCode()) {
            HttpEntity entity = httpResponse.getEntity();
            String json;
            try {
                json = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read HTTP response content", e);
            }

            QueryResponse response = QueryResponse.fromJson(json);

            // We log GraphQL errors because they might otherwise get "silently" unnoticed
            if (response.getErrors() != null) {
                LOGGER.warn("GraphQL request {} returned some errors {}", request.getQuery(), response.getErrors());
            }

            return response;
        } else {
            EntityUtils.consumeQuietly(httpResponse.getEntity());
            throw new RuntimeException("GraphQL query failed with response code " + statusLine.getStatusCode());
        }
    }

    @Override
    public <T, U> GraphQLResponse<T, U> execute(GraphQLRequest request, Type typeOfT, Type typeofU) {
        return execute(request, typeOfT, typeofU, null);
    }

    @Override
    public <T, U> GraphQLResponse<T, U> execute(GraphQLRequest request, Type typeOfT, Type typeofU, RequestOptions options) {
        LOGGER.debug("Executing GraphQL query: " + request.getQuery());
        HttpResponse httpResponse;
        try {
            httpResponse = client.execute(buildRequest(request, options));
        } catch (Exception e) {
            throw new RuntimeException("Failed to send GraphQL request", e);
        }

        StatusLine statusLine = httpResponse.getStatusLine();
        if (HttpStatus.SC_OK == statusLine.getStatusCode()) {
            HttpEntity entity = httpResponse.getEntity();
            String json;
            try {
                json = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read HTTP response content", e);
            }

            Gson gson = (options != null && options.getGson() != null) ? options.getGson() : this.gson;
            Type type = TypeToken.getParameterized(GraphQLResponse.class, typeOfT, typeofU).getType();
            GraphQLResponse<T, U> response = gson.fromJson(json, type);

            // We log GraphQL errors because they might otherwise get "silently" unnoticed
            if (response.getErrors() != null) {
                Type listErrorsType = TypeToken.getParameterized(List.class, typeofU).getType();
                String errors = gson.toJson(response.getErrors(), listErrorsType);
                LOGGER.warn("GraphQL request {} returned some errors {}", request.getQuery(), errors);
            }

            return response;
        } else {
            EntityUtils.consumeQuietly(httpResponse.getEntity());
            throw new RuntimeException("GraphQL query failed with response code " + statusLine.getStatusCode());
        }
    }

    private HttpClient buildHttpClient() throws Exception {
        SSLConnectionSocketFactory sslsf = null;
        if (acceptSelfSignedCertificates) {
            LOGGER.warn("Self-signed SSL certificates are accepted. This should NOT be done on production systems!");
            SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(new TrustAllStrategy()).build();
            sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        } else {
            sslsf = new SSLConnectionSocketFactory(SSLContexts.createDefault(), new DefaultHostnameVerifier());
        }

        // We use a pooled connection manager to support concurrent threads and connections
        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslsf)
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg);
        cm.setMaxTotal(maxHttpConnections);
        cm.setDefaultMaxPerRoute(maxHttpConnections); // we just have one route to the GraphQL endpoint

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectionTimeout)
                .setSocketTimeout(socketTimeout)
                .setConnectionRequestTimeout(requestPoolTimeout)
                .build();

        return HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(cm)
                .disableCookieManagement()
                .build();
    }

    private HttpUriRequest buildRequest(GraphQLRequest request, RequestOptions options) throws UnsupportedEncodingException {
        HttpMethod httpMethod = this.httpMethod;
        if (options != null && options.getHttpMethod() != null) {
            httpMethod = options.getHttpMethod();
        }

        RequestBuilder rb = RequestBuilder.create(httpMethod.toString()).setUri(url);
        rb.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        if (HttpMethod.GET.equals(httpMethod)) {
            rb.addParameter("query", request.getQuery());
            if (request.getOperationName() != null) {
                rb.addParameter("operationName", request.getOperationName());
            }
            if (request.getVariables() != null) {
                String json = gson.toJson(request.getVariables());
                rb.addParameter("variables", json);
            }
        } else {
            rb.setEntity(new StringEntity(gson.toJson(request)));
        }

        if (httpHeaders != null) {
            for (String httpHeader : httpHeaders) {
                // We ignore empty values, this may happen because of the way the AEM OSGi configuration editor works
                if (StringUtils.isBlank(httpHeader)) {
                    continue;
                }

                int idx = httpHeader.indexOf(":");
                if (idx < 1 || httpHeader.length() <= (idx + 1)) {
                    throw new IllegalStateException("The HTTP header is not a name:value pair --> " + httpHeader);
                }
                rb.addHeader(httpHeader.substring(0, idx).trim(), httpHeader.substring(idx + 1).trim());
            }
        }

        if (options != null && options.getHeaders() != null) {
            for (Header header : options.getHeaders()) {
                rb.addHeader(header.getName(), header.getValue());
            }
        }

        return rb.build();
    }
}
