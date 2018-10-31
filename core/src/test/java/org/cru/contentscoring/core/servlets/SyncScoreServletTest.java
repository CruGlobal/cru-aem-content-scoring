package org.cru.contentscoring.core.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
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
}
