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
import org.cru.contentscoring.core.provider.VanityPathUriProvider;
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
        servlet.vanityPathUriProvider = mock(VanityPathUriProvider.class);
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
    public void testGetOnlyAbsoluteUrl() throws Exception {
        String absolutePath = "/content/site/us/en/full/absolute/path";
        StringParameter pathParam = new StringParameter("path", absolutePath);
        StringParameter[] paths = new StringParameter[] {pathParam};

        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRequestParameters("path")).thenReturn(paths);
        when(request.getResourceResolver()).thenReturn(resourceResolver);

        Resource resource = mock(Resource.class);
        when(resourceResolver.getResource(absolutePath)).thenReturn(resource);

        when(servlet.absolutePathUriProvider.toURI(resource, Scope.EXTERNAL, Operation.READ))
            .thenReturn(new URI(BASE_URL + absolutePath + HTML_EXTENSION));

        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);
        when(response.getWriter()).thenReturn(printWriter);

        servlet.doGet(request, response);
        printWriter.flush();

        verify(response).setHeader("Content-Type", "application/json");
        String json = outputStream.toString();
        assertThat(json.contains(BASE_URL + absolutePath + HTML_EXTENSION), is(equalTo(true)));
    }

    @Test
    public void testGetAbsoluteAndVanities() throws Exception {
        String absolutePath = "/content/site/us/en/full/absolute/path";
        String vanityPath = "/path";
        StringParameter absolutePathParam = new StringParameter("path", absolutePath);
        StringParameter vanityPathParam = new StringParameter("path", vanityPath);
        StringParameter[] paths = new StringParameter[] {absolutePathParam, vanityPathParam};

        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRequestParameters("path")).thenReturn(paths);
        when(request.getResourceResolver()).thenReturn(resourceResolver);

        Resource resource = mock(Resource.class);
        when(resourceResolver.getResource(absolutePath)).thenReturn(resource);
        when(resourceResolver.resolve(vanityPath)).thenReturn(resource);

        when(servlet.absolutePathUriProvider.toURI(resource, Scope.EXTERNAL, Operation.READ))
            .thenReturn(new URI(BASE_URL + absolutePath + HTML_EXTENSION));
        when(servlet.vanityPathUriProvider.toURI(vanityPath, resourceResolver))
            .thenReturn(new URI(BASE_URL + vanityPath));

        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);
        when(response.getWriter()).thenReturn(printWriter);

        servlet.doGet(request, response);
        printWriter.flush();

        verify(response).setHeader("Content-Type", "application/json");
        String json = outputStream.toString();
        assertThat(json.contains(BASE_URL + absolutePath + HTML_EXTENSION), is(equalTo(true)));
        assertThat(json.contains(BASE_URL + vanityPath), is(equalTo(true)));
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
