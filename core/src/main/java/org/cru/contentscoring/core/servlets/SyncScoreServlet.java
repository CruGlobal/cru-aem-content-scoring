package org.cru.contentscoring.core.servlets;

import com.google.common.annotations.VisibleForTesting;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.cru.contentscoring.core.service.SyncScoreService;
import org.cru.contentscoring.core.util.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SlingServlet(
    paths = "/bin/content-scoring/sync",
    metatype = true
)
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
        String resourcePath;
        Client client = ClientBuilder.newBuilder().build();
        try {
            Response pathFinderResponse;

            // We're only scoring html pages
            if (incomingUri.endsWith(".html")) {
                incomingUri = incomingUri.replace(".html", "");
                pathFinderResponse = client.target(incomingUri + ".find.path.html")
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
                pathFinderResponse = client.target(serverUri + "/bin/cru/path/finder")
                    .queryParam("path", incomingUri)
                    .request()
                    .get();
            }

            resourcePath = pathFinderResponse.readEntity(String.class);

            if (resourcePath == null || !resourcePath.startsWith("/")) {
                LOG.warn("Resource path not found");
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
