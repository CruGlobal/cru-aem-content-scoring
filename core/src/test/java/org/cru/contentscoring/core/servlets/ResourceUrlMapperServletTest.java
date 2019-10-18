package org.cru.contentscoring.core.servlets;

import com.day.cq.commons.Externalizer;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.external.URIProvider;
import org.apache.sling.api.resource.external.URIProvider.Scope;
import org.apache.sling.api.resource.external.URIProvider.Operation;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ResourceUrlMapperServletTest {
    private static final String DOMAIN = "test-publish";
    private static final String BASE_URL = "http://test.com";
    private static final String HTML_EXTENSION = ".html";

    private ResourceUrlMapperServlet servlet = new ResourceUrlMapperServlet();
    private ResourceResolver resourceResolver;
    private Externalizer externalizer;

    @Before
    public void setup() {
        externalizer = mock(Externalizer.class);
        resourceResolver = mock(ResourceResolver.class);
        when(resourceResolver.adaptTo(Externalizer.class)).thenReturn(externalizer);

        servlet.absolutePathUriProvider = mock(URIProvider.class);
    }

    @Test
    public void testGetWithoutPaths() throws Exception {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRequestParameter("domain")).thenReturn(new StringParameter("domain", DOMAIN));

        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);
        when(response.getWriter()).thenReturn(printWriter);

        servlet.doGet(request, response);

        verify(response).setStatus(400);
        printWriter.flush();
        assertThat(outputStream.toString(), is(equalTo("Path parameter is missing.")));
    }

    @Test
    public void testGetWithoutDomain() throws Exception {
        String pathOne = "/one";
        String pathTwo = "/two";
        String pathThree = "/three";
        StringParameter one = new StringParameter("path", pathOne);
        StringParameter two = new StringParameter("path", pathTwo);
        StringParameter three = new StringParameter("path", pathThree);
        StringParameter[] paths = new StringParameter[] {one, two, three};

        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRequestParameters("path")).thenReturn(paths);
        when(request.getResourceResolver()).thenReturn(resourceResolver);

        Resource resourceOne = mock(Resource.class);
        Resource resourceTwo = mock(Resource.class);
        Resource resourceThree = mock(Resource.class);
        when(resourceResolver.getResource(pathOne)).thenReturn(resourceOne);
        when(resourceResolver.getResource(pathTwo)).thenReturn(resourceTwo);
        when(resourceResolver.getResource(pathThree)).thenReturn(resourceThree);

        when(servlet.absolutePathUriProvider.toURI(resourceOne, Scope.EXTERNAL, Operation.READ))
            .thenReturn(new URI(BASE_URL + pathOne + HTML_EXTENSION));
        when(servlet.absolutePathUriProvider.toURI(resourceTwo, Scope.EXTERNAL, Operation.READ))
            .thenReturn(new URI(BASE_URL + pathTwo + HTML_EXTENSION));
        when(servlet.absolutePathUriProvider.toURI(resourceThree, Scope.EXTERNAL, Operation.READ))
            .thenReturn(new URI(BASE_URL + pathThree + HTML_EXTENSION));

        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);
        when(response.getWriter()).thenReturn(printWriter);

        servlet.doGet(request, response);
        printWriter.flush();

        verify(response).setHeader("Content-Type", "application/json");
        String json = outputStream.toString();
        assertThat(json.contains(BASE_URL + pathOne + HTML_EXTENSION), is(equalTo(true)));
        assertThat(json.contains(BASE_URL + pathTwo + HTML_EXTENSION), is(equalTo(true)));
        assertThat(json.contains(BASE_URL + pathThree + HTML_EXTENSION), is(equalTo(true)));
    }

    @Test
    public void testGet() throws Exception {
        String pagePath = "/content/app/us/en/page";
        String vanityOne = "/us/en/page";
        String vanityTwo = "/page";

        StringParameter one = new StringParameter("path", pagePath);
        StringParameter two = new StringParameter("path", vanityOne);
        StringParameter three = new StringParameter("path", vanityTwo);
        StringParameter[] paths = new StringParameter[] {one, two, three};

        mockExternalLink(pagePath);
        mockExternalLink(vanityOne);
        mockExternalLink(vanityTwo);

        when(resourceResolver.getResource(pagePath)).thenReturn(mock(Resource.class));

        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRequestParameters("path")).thenReturn(paths);
        when(request.getRequestParameter("domain")).thenReturn(new StringParameter("domain", DOMAIN));
        when(request.getResourceResolver()).thenReturn(resourceResolver);

        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);
        when(response.getWriter()).thenReturn(printWriter);

        servlet.doGet(request, response);
        printWriter.flush();

        verify(response).setHeader("Content-Type", "application/json");
        String json = outputStream.toString();
        assertThat(json.contains(BASE_URL + pagePath + HTML_EXTENSION), is(equalTo(true)));
        assertThat(json.contains(BASE_URL + vanityOne), is(equalTo(true)));
        assertThat(json.contains(BASE_URL + vanityTwo), is(equalTo(true)));
    }

    private void mockExternalLink(final String path) {
        when(externalizer.externalLink(resourceResolver, DOMAIN, path)).thenReturn(BASE_URL + path);
    }

    private class StringParameter implements RequestParameter {
        String name;
        String value;

        StringParameter(final String name, final String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isFormField() {
            return false;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public long getSize() {
            return 0;
        }

        @Override
        public byte[] get() {
            return value.getBytes();
        }

        @Override
        public InputStream getInputStream() {
            return null;
        }

        @Override
        public String getFileName() {
            return null;
        }

        @Override
        public String getString() {
            return value;
        }

        @Override
        public String getString(final String s) {
            return value;
        }
    }
}
