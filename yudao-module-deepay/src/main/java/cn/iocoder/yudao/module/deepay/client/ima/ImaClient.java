package cn.iocoder.yudao.module.deepay.client.ima;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;

    public ImaClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
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
        HttpEntity<CreateKbRequest> entity = new HttpEntity<>(request, buildHeaders());
        CreateKbResponse response = restTemplate.postForObject(
                baseUrl + "/openapi/v1/knowledge-base", entity, CreateKbResponse.class);
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
        HttpEntity<UploadDocumentRequest> entity = new HttpEntity<>(request, buildHeaders());
        restTemplate.postForObject(
                baseUrl + "/openapi/v1/knowledge-base/" + kbId + "/document", entity, Void.class);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        return headers;
    }

    // -------------------------------- 请求 / 响应 VO --------------------------------

    @Data
    private static class CreateKbRequest {
        @JsonProperty("name")
        private final String name;
        @JsonProperty("description")
        private final String description;

        CreateKbRequest(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateKbResponse {
        /** ima 知识库 ID */
        @JsonProperty("id")
        private String id;
    }

    @Data
    private static class UploadDocumentRequest {
        @JsonProperty("url")
        private final String url;
        @JsonProperty("type")
        private final String type;

        UploadDocumentRequest(String url, String type) {
            this.url = url;
            this.type = type;
        }
    }

}
