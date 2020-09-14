package org.cru.contentscoring.core.provider;

import com.google.common.collect.Lists;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.cru.contentscoring.core.util.SystemUtils;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VanityPathUriProviderTest {
    private VanityPathUriProvider vanityPathUriProvider;
    private ResourceResolver resourceResolver;

    @Before
    public void setup() throws Exception {
        resourceResolver = mock(ResourceResolver.class);
        SystemUtils mockSystemUtils = mock(SystemUtils.class);
        when(mockSystemUtils.getResourceResolver(anyString())).thenReturn(resourceResolver);
        vanityPathUriProvider = new VanityPathUriProvider("local", mockSystemUtils);
        mockSlingMaps();
    }

    @Test
    public void testToUri() {
        URI uri = vanityPathUriProvider.toURI("/content/primary/us/en/wherever", resourceResolver);
        assertThat(uri.toString(), is(equalTo("https://primary.site.org/us/en/wherever")));
    }

    @Test
    public void testInvalidVanityReturnsNull() {
        URI uri = vanityPathUriProvider.toURI("/0123456", resourceResolver);
        assertThat(uri, is(nullValue()));
    }

    private void mockSlingMaps() {
        Resource primarySiteMap = mockResource(null, "primary.site.org", "/content/primary", false, null);
        Resource primarySiteRedirect = mockResource(null, "primary.site_org", "/content/primary/us/en", false, null);

        String httpsPath = "/etc/map.publish.local/https";
        Resource httpsMap = mockResource(
            httpsPath,
            "https",
            null,
            true,
            Lists.newArrayList(primarySiteRedirect, primarySiteMap));

        when(primarySiteRedirect.getParent()).thenReturn(httpsMap);
        when(primarySiteMap.getParent()).thenReturn(httpsMap);

        Resource otherSiteMap = mockResource(null, "other.site.com", "/content/other", false, null);
        Resource otherSiteRedirect = mockResource(null, "other.site_com", "/content/other/us/en", false, null);

        String httpPath = "/etc/map.publish.local/http";
        Resource httpMap = mockResource(
            httpPath,
            "http",
            null,
            true,
            Lists.newArrayList(otherSiteRedirect, otherSiteMap));

        when(otherSiteRedirect.getParent()).thenReturn(httpMap);
        when(otherSiteMap.getParent()).thenReturn(httpMap);

        when(resourceResolver.getResource(httpsPath)).thenReturn(httpsMap);
        when(resourceResolver.getResource(httpPath)).thenReturn(httpMap);
    }

    private Resource mockResource(
        final String path,
        final String name,
        final String internalRedirect,
        final boolean hasChildren,
        final List<Resource> children) {

        Resource resource = mock(Resource.class);
        when(resource.getName()).thenReturn(name);
        when(resource.getPath()).thenReturn(path);
        when(resource.hasChildren()).thenReturn(hasChildren);
        when(resource.getChildren()).thenReturn(children);

        Map<String, Object> properties = new HashMap<>();
        properties.put("sling:internalRedirect", internalRedirect);

        ValueMap valueMap = new ValueMapDecorator(properties);
        when(resource.getValueMap()).thenReturn(valueMap);

        return resource;
    }
}
