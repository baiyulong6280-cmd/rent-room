package cn.iocoder.yudao.module.deepay.framework.security.config;

import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Deepay 模块 Security 配置。
 *
 * <p>将 {@code /api/create-product} 设为无需认证的公开接口，
 * 方便 MVP 阶段直接通过 curl 或 Postman 测试。</p>
 */
@Configuration(proxyBeanMethods = false, value = "deepaySecurityConfiguration")
public class SecurityConfiguration {

    @Bean("deepayAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                registry.requestMatchers(
                        "/api/create-product",       // 轻量版（向后兼容）
                        "/deepay/run",               // 完整生产流水线
                        "/deepay/trend",             // 趋势查询
                        "/deepay/callback/payment"   // 支付回调（Webhook，无 Token）
                ).permitAll();
            }

        };
    }

}
