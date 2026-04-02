package com.knowledge.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${ai.api.base-url}")
    private String aiApiBaseUrl;

    @Value("${ai.api.key}")
    private String aiApiKey;

    @Bean("aiWebClient")
    public WebClient aiWebClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            // TCP_NODELAY=true 禁用 Nagle 算法，每个 token 立即写出不等待凑包
            .option(ChannelOption.TCP_NODELAY, true)
            .responseTimeout(Duration.ofMinutes(5))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(300, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
            .baseUrl(aiApiBaseUrl)
            .defaultHeader("Authorization", "Bearer " + aiApiKey)
            .defaultHeader("Content-Type", "application/json")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            // 流式场景不需要大 buffer，减小以降低内存压力
            .codecs(conf -> conf.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();
    }
}
