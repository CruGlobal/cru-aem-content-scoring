package org.cru.contentscoring.core.servlets;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import java.io.IOException;

/**
 * This resource finder servlet is the primary one to find resource paths. It handles most URLs,
 * but not things like the home page and pages that don't end with .html, because these can't have selectors.
 */
@SlingServlet(
    resourceTypes = "cq/Page",
    methods = "GET",
    selectors = "find.path"
)
public class ResourcePathFinderServlet extends SlingSafeMethodsServlet {
    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException {
        response.getWriter().write(request.getResource().getPath());
    }
}
