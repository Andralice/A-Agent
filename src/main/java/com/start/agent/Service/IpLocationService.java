package com.start.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 通过 ip-api.com 免费接口解析 IP 归属地（不传 key 则每分钟 45 次限制）。
 */
@Slf4j
@Service
public class IpLocationService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** 解析 IP，返回 "国家/城市" 格式，如 "中国/杭州"。失败返回 null。 */
    public String resolve(String ip) {
        if (ip == null || ip.isBlank() || isLocalIp(ip)) {
            return "本地";
        }
        try {
            String url = "http://ip-api.com/json/" + ip + "?lang=zh-CN&fields=country,regionName,city";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            JsonNode root = objectMapper.readTree(resp.body());
            String country = root.path("country").asText("");
            String region = root.path("regionName").asText("");
            String city = root.path("city").asText("");
            if (country.isEmpty()) return null;
            StringBuilder sb = new StringBuilder(country);
            if (!region.isEmpty() && !region.equals(city)) sb.append("/").append(region);
            if (!city.isEmpty()) sb.append("/").append(city);
            return sb.toString();
        } catch (Exception e) {
            log.debug("IP 归属地解析失败: {} - {}", ip, e.getMessage());
            return null;
        }
    }

    private static boolean isLocalIp(String ip) {
        return ip.startsWith("127.") || ip.startsWith("192.168.") || ip.startsWith("10.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.")
                || ip.startsWith("172.19.") || ip.startsWith("172.20.") || ip.startsWith("172.21.")
                || ip.startsWith("172.22.") || ip.startsWith("172.23.") || ip.startsWith("172.24.")
                || ip.startsWith("172.25.") || ip.startsWith("172.26.") || ip.startsWith("172.27.")
                || ip.startsWith("172.28.") || ip.startsWith("172.29.") || ip.startsWith("172.30.")
                || ip.startsWith("172.31.") || ip.startsWith("0.") || ip.equals("0:0:0:0:0:0:0:1")
                || ip.startsWith("::1");
    }
}
