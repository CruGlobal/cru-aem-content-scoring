package org.cru.contentscoring.core.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.cru.contentscoring.core.provider.AbsolutePathUriProvider;
import org.cru.contentscoring.core.provider.VanityPathUriProvider;
import org.cru.contentscoring.core.util.SystemUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ResourceUrlMapperServletTest {
    private static final String DOMAIN = "test-publish";
    private static final String BASE_URL = "http://test.com";
    private static final String HTML_EXTENSION = ".html";

    private ResourceUrlMapperServlet servlet = new ResourceUrlMapperServlet();
    private ResourceResolver resourceResolver;

    @Before
    public void setup() throws Exception {
        resourceResolver = mock(ResourceResolver.class);
        servlet.absolutePathUriProvider = mock(AbsolutePathUriProvider.class);
        servlet.vanityPathUriProvider = mock(VanityPathUriProvider.class);

        SystemUtils mockSystemUtils = mock(SystemUtils.class);
        when(mockSystemUtils.getResourceResolver(anyString())).thenReturn(resourceResolver);
        servlet.systemUtils = mockSystemUtils;
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

        when(servlet.absolutePathUriProvider.toURI(resource, resourceResolver))
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

        when(servlet.absolutePathUriProvider.toURI(resource, resourceResolver))
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
