package cz.cas.lib.arcstorage.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.ArrayList;

import static cz.cas.lib.arcstorage.util.Utils.asList;

@EnableSwagger2
@Configuration
public class SwaggerConfig {

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
//                .globalOperationParameters(
//                        asList(new ParameterBuilder()
//                                .name("Authorization")
//                                .modelRef(new ModelRef("string"))
//                                .parameterType("header")
//                                .defaultValue("Bearer ")
//                                .build()))
                .select()
                .apis(RequestHandlerSelectors.basePackage("cz.cas.lib"))
                .paths(PathSelectors.any())
                .build()
                .apiInfo(apiInfo());
    }

    private ApiInfo apiInfo() {
        return new ApiInfo(
                "Archival Storage Gateway API",
                "",
                "v1",
                null,
                new Contact("", "", ""),
                null, null, new ArrayList<>());
    }
}
