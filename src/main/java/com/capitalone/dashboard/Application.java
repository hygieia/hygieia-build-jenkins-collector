package com.capitalone.dashboard;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.net.ssl.HttpsURLConnection;

/**
 * Application configuration and bootstrap
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        HttpsURLConnection.setDefaultHostnameVerifier(new NoopHostnameVerifier());
        SpringApplication.run(Application.class, args);
    }
}
