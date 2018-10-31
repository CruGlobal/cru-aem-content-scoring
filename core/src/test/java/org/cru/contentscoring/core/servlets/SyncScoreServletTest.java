package org.cru.contentscoring.core.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class SyncScoreServletTest {
    private SyncScoreServlet syncScoreServlet = new SyncScoreServlet();

    private String invalidScore;
    private String validScore;

    @Parameters
    public static Collection<Object[]> testData() {
        return Arrays.asList(new Object[][] {
            { "a", "0" },
            { "-1", "1" },
            { "11", "10" },
            { "4.5", "5" },
            { "", "7" },
            { null, "3" }
        });
    }

    public SyncScoreServletTest(
        final String invalidScore,
        final String validScore) {

        this.invalidScore = invalidScore;
        this.validScore = validScore;
    }

    @Test
    public void testInvalidScore() {
        assertThat(syncScoreServlet.scoreIsValid(invalidScore), is(equalTo(false)));
    }

    @Test
    public void testValidScore() {
        assertThat(syncScoreServlet.scoreIsValid(validScore), is(equalTo(true)));
    }

    @Test
    public void testMobileAppIsSkipped() throws Exception {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);

        when(request.getParameter("resourceUri[href]")).thenReturn("someapp://some/path");
        when(request.getParameter("score")).thenReturn("8");
        try {
            syncScoreServlet.doPost(request, response);
        } catch (MalformedURLException e) {
            fail();
        }
    }

    @Test
    public void testUrlWithoutHtml() throws Exception {
        String incomingUri = "https://somewhere.com/path";
        String resourcePath = "/content/somewhere/us/en/path";

        Response mockResponse = mock(Response.class);
        when(mockResponse.readEntity(String.class)).thenReturn(resourcePath);

        Builder mockBuilder = mock(Builder.class);
        when(mockBuilder.get()).thenReturn(mockResponse);

        WebTarget mockTarget = mock(WebTarget.class);
        when(mockTarget.queryParam(anyString(), anyString())).thenReturn(mockTarget);
        when(mockTarget.request()).thenReturn(mockBuilder);

        Client client = mock(Client.class);
        when(client.target("https://somewhere.com/bin/cru/path/finder.txt")).thenReturn(mockTarget);


        String returnedPath = syncScoreServlet.determineResourcePath(client, incomingUri);
        assertThat(returnedPath, is(equalTo(resourcePath)));
    }

    @Test
    public void testUrlWithHtml() throws Exception {
        String incomingUri = "http://somewhere.com/path.html";
        String resourcePath = "/content/somewhere/us/en/path";

        Response mockResponse = mock(Response.class);
        when(mockResponse.readEntity(String.class)).thenReturn(resourcePath);

        Builder mockBuilder = mock(Builder.class);
        when(mockBuilder.get()).thenReturn(mockResponse);

        WebTarget mockTarget = mock(WebTarget.class);
        when(mockTarget.request()).thenReturn(mockBuilder);

        Client client = mock(Client.class);
        when(client.target("http://somewhere.com/path.find.path.txt")).thenReturn(mockTarget);


        String returnedPath = syncScoreServlet.determineResourcePath(client, incomingUri);
        assertThat(returnedPath, is(equalTo(resourcePath)));
    }
}
