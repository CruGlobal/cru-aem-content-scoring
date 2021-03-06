package org.cru.contentscoring.core.servlets;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.Servlet;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.apache.http.client.utils.URIBuilder;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.cru.contentscoring.core.service.SyncScoreService;
import org.cru.contentscoring.core.util.SystemUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.paths=/bin/cru/content-scoring/sync" })
public class SyncScoreServlet extends SlingAllMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(SyncScoreServlet.class);

    private static final String SUBSERVICE = "contentScoreSync";

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Reference
    private SyncScoreService syncScoreService;

    @Reference
    private SystemUtils systemUtils;

    @Override
    protected void doPost(
        final SlingHttpServletRequest request,
        final SlingHttpServletResponse response) throws IOException {

        int score;
        if (scoreIsValid(request.getParameter("score"))) {
            score = Integer.valueOf(request.getParameter("score"));
        } else {
            response.sendError(400, "Invalid Score");
            return;
        }

        String incomingUri = request.getParameter("resourceUri[href]");
        if (!incomingUri.startsWith("http")) {
            LOG.debug("Non web URI came in. This is not an AEM property, so skip sync.");
            return;
        }

        String resourcePath;
        Client client = ClientBuilder.newBuilder().build();
        try {
            resourcePath = determineResourcePath(client, incomingUri);
            if (resourcePath == null) {
                return;
            }
        } catch (URISyntaxException e) {
          LOG.error(e.getMessage());
          return;
        } finally {
            client.close();
        }



        executor.submit(() -> {
            try (ResourceResolver resourceResolver = systemUtils.getResourceResolver(SUBSERVICE)) {
                syncScoreService.syncScore(
                    resourceResolver,
                    score,
                    resourcePath);
            } catch (Exception e) {
                LOG.error("Failed to sync score from scale-of-belief-lambda", e);
            }
        });
    }

    @VisibleForTesting
    String determineResourcePath(final Client client, final String incomingUri) throws URISyntaxException {
        Response pathFinderResponse;

        // We're only scoring html pages
        if (incomingUri.endsWith(".html")) {
            pathFinderResponse = client.target(incomingUri.replace(".html", "") + ".find.path.txt")
                .request()
                .get();
        } else {
            URI uri = new URI(incomingUri);
            // This should be the load-balanced URL (e.g. https://www.cru.org), but could be a publisher URL.
            String serverUri = new URIBuilder()
                .setScheme(uri.getScheme())
                .setPort(uri.getPort())
                .setHost(uri.getHost())
                .build()
                .toString();
            LOG.debug("Calling {} with path {}", serverUri + "/bin/cru/path/finder.txt", incomingUri);
            pathFinderResponse = client.target(serverUri + "/bin/cru/path/finder.txt")
                .queryParam("path", incomingUri)
                .request()
                .get();
        }

        String resourcePath = pathFinderResponse.readEntity(String.class);

        if (Strings.isNullOrEmpty(resourcePath) || !resourcePath.startsWith("/")) {
            LOG.warn("Resource path not found");
            return null;
        }
        return resourcePath;
    }

    @VisibleForTesting
    boolean scoreIsValid(final String scoreParameter) {
        int score;
        try {
            score = Integer.valueOf(scoreParameter);
        } catch (NumberFormatException nfe) {
            return false;
        }

        return score >= 0 && score <= 10;
    }
}
