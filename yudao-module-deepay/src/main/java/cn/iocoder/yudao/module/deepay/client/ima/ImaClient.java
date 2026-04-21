package cn.iocoder.yudao.module.deepay.client.ima;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * ima 知识库 HTTP 客户端。
 *
 * <p>封装对 ima REST API 的底层调用，仅供 {@link cn.iocoder.yudao.module.deepay.service.ima.ImaService} 使用。</p>
 *
 * <p>API 约定（以 ima.qq.com OpenAPI 为基础）：
 * <ul>
 *   <li>创建知识库：{@code POST /openapi/v1/knowledge-base}</li>
 *   <li>上传图片：{@code POST /openapi/v1/knowledge-base/{kbId}/document}</li>
 * </ul>
 * </p>
 */
public class ImaClient {

    private final RestClient restClient;

    public ImaClient(String baseUrl, String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 创建知识库。
     *
     * @param name        知识库名称（通常使用 chainCode）
     * @param description 知识库描述
     * @return 创建成功的知识库 ID
     */
    public String createKnowledgeBase(String name, String description) {
        CreateKbRequest request = new CreateKbRequest(name, description);
        CreateKbResponse response = restClient.post()
                .uri("/openapi/v1/knowledge-base")
                .body(request)
                .retrieve()
                .body(CreateKbResponse.class);
        if (response == null || response.getId() == null) {
            throw new IllegalStateException("ima 返回的知识库 ID 为空，name=" + name);
        }
        return response.getId();
    }

    /**
     * 向知识库上传图片文档（以 URL 形式引用）。
     *
     * @param kbId     知识库 ID
     * @param imageUrl 图片公网 URL
     */
    public void uploadImage(String kbId, String imageUrl) {
        UploadDocumentRequest request = new UploadDocumentRequest(imageUrl, "image");
        restClient.post()
                .uri("/openapi/v1/knowledge-base/{kbId}/document", kbId)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    // -------------------------------- 请求 / 响应 VO --------------------------------

    private record CreateKbRequest(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description) {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateKbResponse {
        /** ima 知识库 ID */
        @JsonProperty("id")
        private String id;
    }

    private record UploadDocumentRequest(
            @JsonProperty("url") String url,
            @JsonProperty("type") String type) {
    }

}
