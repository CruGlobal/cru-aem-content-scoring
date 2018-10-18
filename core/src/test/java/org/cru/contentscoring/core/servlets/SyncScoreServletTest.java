package org.cru.contentscoring.core.servlets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class SyncScoreServletTest {
    private SyncScoreServlet syncScoreServlet = new SyncScoreServlet();

    private String invalidScore;
    private String validScore;
    private String webPath;
    private String resourcePath;

    @Parameters
    public static Collection<Object[]> testData() {
        return Arrays.asList(new Object[][] {
            { "a", "0", "/some/path.html", "/some/path" },
            { "-1", "1", "/some/path", "/some/path" },
            { "11", "10", "/some/path.pdf", "/some/path" },
            { "4.5", "5", "/some/image.jpg", "/some/image" },
            { "", "7", "/some/really/long/path/that/is/very/deep.html", "/some/really/long/path/that/is/very/deep" },
            { null, "3", "/path", "/path" }
        });
    }

    public SyncScoreServletTest(
        final String invalidScore,
        final String validScore,
        final String webPath,
        final String resourcePath) {

        this.invalidScore = invalidScore;
        this.validScore = validScore;
        this.webPath = webPath;
        this.resourcePath = resourcePath;
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
    public void testRemoveExtension() {
        assertThat(syncScoreServlet.removeExtension(webPath), is(equalTo(resourcePath)));
    }
}
