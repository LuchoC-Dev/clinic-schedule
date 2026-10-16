package com.clinic.schedule.shared.webapp;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the two single-page applications Maven builds into {@code static/} (D-97, D-114): the staff's at {@code /},
 * the patients' at {@code /portal/}.
 *
 * <p>An address of the application's own — {@code /agenda}, or {@code /portal/activation?token=…} from an email —,
 * typed, reloaded or opened from a link, is answered with its {@code index.html}, and the application's router shows
 * it. A file that is not there, one with an extension, is still a 404, and so is anything under {@code /api/} that no
 * controller answers: the API's addresses are never a page.
 */
@Configuration
class WebApplicationConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // The more specific pattern wins, so the patients' addresses never reach the staff's application.
        registry.addResourceHandler("/portal/**")
                .addResourceLocations("classpath:/static/portal/")
                .resourceChain(true)
                .addResolver(new ApplicationRouteResolver());
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/staff/")
                .resourceChain(true)
                .addResolver(new ApplicationRouteResolver());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/portal", "/portal/");
        // The resources' handler answers nothing for its own root: the page is asked for by name.
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/portal/").setViewName("forward:/portal/index.html");
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
            return last.contains(".") || resourcePath.startsWith("api/") ? null
                    : super.getResource("index.html", location);
        }
    }
}
