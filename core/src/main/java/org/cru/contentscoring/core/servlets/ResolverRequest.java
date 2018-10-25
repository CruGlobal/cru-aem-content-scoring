package org.cru.contentscoring.core.servlets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Taken from http://www.hsufengko.com/blog/programmatically-find-the-sling-resource-from-a-given-a-url.
 * Allows us to resolve resource paths from URLs.
 */
public class ResolverRequest extends HttpServletRequestWrapper {

    private final URI uri;

    public ResolverRequest(final HttpServletRequest request, final String uriString) throws URISyntaxException {
        super(request);
        this.uri = new URI(uriString);
    }

    @Override
    public String getScheme() {
        return uri.getScheme();
    }

    @Override
    public String getServerName() {
        return uri.getHost();
    }

    @Override
    public int getServerPort() {
        return uri.getPort();
    }

    @Override
    public String getPathInfo() {
        return uri.getPath();
    }
}
