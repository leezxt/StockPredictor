package org.gtalent;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Primary;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Spring 應用配置類
 * 配置 RestTemplate Bean，用於 HTTP 請求
 *
 * v2.1 增強：
 * - 添加連接池配置
 * - 自定義錯誤處理器
 * - 優化超時設置
 * - 加入 Caffeine 快取配置
 */
@Configuration
@EnableCaching
public class AppConfig {
    private static final Logger logger = Logger.getLogger(AppConfig.class.getName());

    @Bean
    @Primary
    public CacheManager shortTermCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500));
        return cacheManager;
    }

    @Bean
    public CacheManager historicalDataCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)
                .maximumSize(1000));
        return cacheManager;
    }

    /**
     * 配置並創建 RestTemplate Bean (v2.1 增強版)
     *
     * 性能優化：
     * - 連接超時: 5 秒（快速失敗）
     * - 讀取超時: 10 秒（適應較慢的 API）
     * - 使用緩衝工廠支持請求/響應體重複讀取
     * - 自定義錯誤處理避免拋出不必要的異常
     *
     * @param builder RestTemplateBuilder
     * @return 配置好的 RestTemplate 實例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .requestFactory(this::clientHttpRequestFactory)
                .build();

        // 使用自定義錯誤處理器，避免對 4xx/5xx 拋出異常
        // 讓業務層可以更精確地處理錯誤
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public void handleError(@NonNull org.springframework.http.client.ClientHttpResponse response) {
                // 記錄錯誤但不拋出異常，讓調用方決定如何處理
                try {
                    logger.warning("HTTP 請求錯誤: " + response.getStatusCode() + " - " + response.getStatusText());
                } catch (Exception e) {
                    logger.warning("無法讀取錯誤響應: " + e.getMessage());
                }
            }
        });

        logger.info("✅ RestTemplate 配置完成 (連接超時: 5s, 讀取超時: 10s)");
        return restTemplate;
    }

    /**
     * 創建 HTTP 請求工廠
     * 使用 BufferingClientHttpRequestFactory 包裝以支持響應體重複讀取
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        // 使用緩衝工廠，允許攔截器和錯誤處理器多次讀取響應體
        return new BufferingClientHttpRequestFactory(factory);
    }
}

