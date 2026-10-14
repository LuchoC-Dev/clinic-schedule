package com.clinic.schedule.shared.webapp;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the two single-page applications Maven builds into {@code static/} (D-97): the staff's under
 * {@code /app/} and the patients' under {@code /portal/app/}, while the Thymeleaf screens keep their paths (D-02).
 *
 * <p>An address of the application's own — {@code /app/agenda}, typed, reloaded or opened from a link — is
 * answered with its {@code index.html}, and the application's router shows it. A file that is not there, one
 * with an extension, is still a 404.
 */
@Configuration
class WebApplicationConfiguration implements WebMvcConfigurer {

    private static final String[] APPLICATIONS = {"/app", "/portal/app"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        for (String application : APPLICATIONS) {
            registry.addResourceHandler(application + "/**")
                    .addResourceLocations("classpath:/static" + application + "/")
                    .resourceChain(true)
                    .addResolver(new ApplicationRouteResolver());
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String application : APPLICATIONS) {
            registry.addRedirectViewController(application, application + "/");
            // The resources' handler answers nothing for its own root: the page is asked for by name.
            registry.addViewController(application + "/").setViewName("forward:" + application + "/index.html");
        }
    }

    /** The file asked for when it is there; otherwise, for a route of the application, its index. */
    private static final class ApplicationRouteResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource file = super.getResource(resourcePath, location);
            if (file != null) {
                return file;
            }
            String last = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            return last.contains(".") ? null : super.getResource("index.html", location);
        }
    }
}
