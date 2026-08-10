package com.lzcer.interfaceplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String route : new String[]{"/dashboard", "/interfaces", "/datasources", "/sql-apis", "/logs", "/settings"}) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
