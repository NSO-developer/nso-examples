package com.tailf.packages.ned.netsimgen;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public final class RestconfClient {
    private final HttpClient http;
    private final URI base;
    private final String auth;

    public RestconfClient(URI base, String user, String pass, int connectMs, int readMs) {
        this.base = base;
        this.auth = basic(user, pass);

        try {
            // DEV-ONLY: disable hostname verification for JDK HttpClient
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

            SSLContext insecure = insecureTrustAll();

            // Important: use empty string ("") to disable endpoint identification.
            SSLParameters sslParams = new SSLParameters();
            sslParams.setEndpointIdentificationAlgorithm("");

            this.http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectMs))
                    .version(HttpClient.Version.HTTP_1_1) // keep it simple
                    .sslContext(insecure)
                    .sslParameters(sslParams)
                    .build();

        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize insecure SSLContext", e);
        }
    }

    public HttpResponse<String> get(String rel, String accept) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(base.resolve(rel))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", accept)
            .header("Authorization", auth)
            .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> putJson(String rel, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(base.resolve(rel))
            .header("Content-Type", "application/yang-data+json")
            .header("Authorization", auth)
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> postJson(String rel, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(base.resolve(rel))
            .header("Content-Type", "application/yang-data+json")
            .header("Authorization", auth)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> postXml(String rel, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(base.resolve(rel))
            .header("Content-Type", "application/yang-data+xml")
            .header("Authorization", auth)
            .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""))
            .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> delete(String rel) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(base.resolve(rel))
            .header("Authorization", auth)
            .DELETE().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> patchYang(String rel, String yangPatchJson) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(base.resolve(rel))
            .header("Content-Type", "application/yang-patch+json")
            .header("Authorization", auth)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(yangPatchJson))
            .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    // DEV-ONLY: trust all server certs
    private static SSLContext insecureTrustAll() throws Exception {
        TrustManager[] tms = new TrustManager[] {
            new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tms, new SecureRandom());
        return ctx;
    }

    private static String basic(String user, String pass) {
        String token = Base64.getEncoder()
            .encodeToString((user + ":" + (pass != null ? pass : "")).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
