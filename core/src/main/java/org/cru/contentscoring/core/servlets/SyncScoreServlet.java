package org.cru.contentscoring.core.servlets;

import com.google.common.annotations.VisibleForTesting;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.cru.contentscoring.core.service.SyncScoreService;
import org.cru.contentscoring.core.util.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SlingServlet(
    paths = {"/bin/content-scoring/sync"},
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

        final String resourcePath = request.getParameter("resourceUri[pathname]");
        final String resourceHost = request.getParameter("resourceUri[hostname]");
        final String resourceProtocol = request.getParameter("resourceUri[protocol]");

        if (resourceHost == null || resourcePath == null || resourceProtocol == null) {
            response.sendError(400, "Invalid resource URI");
            return;
        }

        executor.submit(() -> {
            try (ResourceResolver resourceResolver = systemUtils.getResourceResolver(SUBSERVICE)){
                syncScoreService.syncScore(
                    resourceResolver,
                    score,
                    resourcePath,
                    resourceHost,
                    resourceProtocol.replace(":", ""));
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
