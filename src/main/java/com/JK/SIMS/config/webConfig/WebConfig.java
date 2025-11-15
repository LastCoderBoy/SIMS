package com.JK.SIMS.config.webConfig;


import com.JK.SIMS.service.generalUtils.converters.ProductCategoriesConverter;
import com.JK.SIMS.service.generalUtils.converters.PurchaseOrderStatusConverter;
import com.JK.SIMS.service.generalUtils.converters.SalesOrderStatusConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

// Allow frontend to call the backend APIs
// Configuration of CORS
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed.origins}")
    private String[] allowedOrigins;

    @Value("${cors.allowed.methods}")
    private String[] allowedMethods;

    @Value("${cors.allow.credentials}")
    private boolean allowCredentials;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(Arrays.asList(allowedMethods));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(allowCredentials); // Required for cookies
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new PurchaseOrderStatusConverter());
        registry.addConverter(new ProductCategoriesConverter());
        registry.addConverter(new SalesOrderStatusConverter());
    }
}
