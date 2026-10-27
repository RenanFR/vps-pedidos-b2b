package dev.renanrodrigues.orders.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ordersOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pedidos B2B")
                        .version("1.0.0")
                        .description("Cadastro, consulta e ciclo de vida de pedidos de parceiros comerciais, "
                                + "com limite de crédito por parceiro e notificação das mudanças de status."));
    }
}
