package com.learnspark.ai.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnspark.ai.search.dto.SearchResultItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bing Web Search 服务（任务 2.1.1）。
 *
 * <p>调用 Bing Web Search API v7，返回精简的搜索结果列表。
 * 失败时降级返回空列表（不抛异常），保证路线生成主流程不被搜索故障阻断。
 *
 * @see <a href="https://learn.microsoft.com/en-us/bing/search-apis/bing-web-search/">Bing Web Search API</a>
 */
@Slf4j
@Service
public class BingSearchService {

    private static final String BING_ENDPOINT = "https://api.bing.microsoft.com/v7.0/search";
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_RESULT_COUNT = 5;
    private static final int SNIPPET_MAX_LENGTH = 300;

    /**
     * 执行 Bing 搜索。
     *
     * @param apiKey  Bing API Key（Ocp-Apim-Subscription-Key）
     * @param query   搜索关键词
     * @return 搜索结果列表（最多 5 条），失败返回空列表
     */
    public List<SearchResultItem> search(String apiKey, String query) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(query)) {
            return List.of();
        }

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
            factory.setReadTimeout(READ_TIMEOUT_MS);

            RestClient client = RestClient.builder()
                    .requestFactory(factory)
                    .build();

            JsonNode response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.bing.microsoft.com")
                            .path("/v7.0/search")
                            .queryParam("q", query)
                            .queryParam("count", MAX_RESULT_COUNT)
                            .queryParam("mkt", "zh-CN")
                            .build())
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            return parseResults(response);
        } catch (RestClientException e) {
            log.warn("Bing 搜索网络异常: query={}, error={}", query, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("Bing 搜索失败: query={}, error={}", query, e.getMessage());
            return List.of();
        }
    }

    /** 解析 Bing 响应中的 webPages.value 数组 */
    private List<SearchResultItem> parseResults(JsonNode response) {
        if (response == null) {
            return List.of();
        }

        JsonNode webPages = response.path("webPages");
        JsonNode values = webPages.path("value");
        if (!values.isArray() || values.isEmpty()) {
            return List.of();
        }

        List<SearchResultItem> results = new ArrayList<>(values.size());
        for (JsonNode item : values) {
            String title = item.path("name").asText("");
            String url = item.path("url").asText("");
            String snippet = item.path("snippet").asText("");

            // 截断摘要
            if (snippet.length() > SNIPPET_MAX_LENGTH) {
                snippet = snippet.substring(0, SNIPPET_MAX_LENGTH) + "...";
            }

            results.add(new SearchResultItem(title, url, snippet));
        }
        return results;
    }

    /** 将搜索结果列表格式化为 AI 可读的文本摘要 */
    public String formatResults(List<SearchResultItem> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResultItem item = results.get(i);
            sb.append(i + 1).append(". ").append(item.title()).append("\n");
            sb.append("   ").append(item.snippet()).append("\n");
        }
        return sb.toString();
    }
}
