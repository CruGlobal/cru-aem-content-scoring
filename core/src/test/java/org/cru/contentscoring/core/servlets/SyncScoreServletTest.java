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
}
