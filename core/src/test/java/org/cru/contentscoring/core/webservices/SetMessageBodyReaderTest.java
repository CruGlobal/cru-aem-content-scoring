package org.cru.contentscoring.core.webservices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.glassfish.hk2.utilities.reflection.ParameterizedTypeImpl;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class SetMessageBodyReaderTest {
    private SetMessageBodyReader messageBodyReader = new SetMessageBodyReader();

    @Test
    public void testShouldBeReadable() {
        ParameterizedType type = new ParameterizedTypeImpl(Set.class, String.class);

        boolean isReadable = messageBodyReader.isReadable(Set.class, type, null, null);
        assertThat(isReadable, is(equalTo(true)));
    }

    @Test
    public void testShouldNotBeReadableBecauseDifferentType() {
        boolean isReadable = messageBodyReader.isReadable(String.class, String.class, null, null);
        assertThat(isReadable, is(equalTo(false)));
    }

    @Test
    public void testShouldNotBeReadableBecauseDifferentGeneric() {
        ParameterizedType type = new ParameterizedTypeImpl(Set.class, Boolean.class);

        boolean isReadable = messageBodyReader.isReadable(Set.class, type, null, null);
        assertThat(isReadable, is(equalTo(false)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReadSet() {
        ParameterizedType type = new ParameterizedTypeImpl(Set.class, String.class);

        JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(false);
        JsonNode one = new TextNode("one");
        JsonNode two = new TextNode("two");
        JsonNode three = new TextNode("three");

        JsonNode arrayNode = new ArrayNode(jsonNodeFactory, Lists.newArrayList(one, two, three));

        InputStream inputStream = IOUtils.toInputStream(arrayNode.toString(), Charset.forName("utf-8"));
        Set<String> actual = messageBodyReader.readFrom(Set.class, type, null, null, null, inputStream);

        Set<String> expected = new HashSet<>();
        expected.add("one");
        expected.add("two");
        expected.add("three");

        assertThat(actual.size(), is(equalTo(3)));
        assertThat(actual, hasItems(expected.toArray(new String[0])));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReadSetWithDuplicates() {
        ParameterizedType type = new ParameterizedTypeImpl(Set.class, String.class);

        JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(false);
        JsonNode one = new TextNode("one");
        JsonNode two = new TextNode("two");
        JsonNode three = new TextNode("three");
        JsonNode threeSecond = new TextNode("three");

        JsonNode arrayNode = new ArrayNode(jsonNodeFactory, Lists.newArrayList(one, two, three, threeSecond));

        InputStream inputStream = IOUtils.toInputStream(arrayNode.toString(), Charset.forName("utf-8"));
        Set<String> actual = messageBodyReader.readFrom(Set.class, type, null, null, null, inputStream);

        Set<String> expected = new HashSet<>();
        expected.add("one");
        expected.add("two");
        expected.add("three");

        assertThat(actual.size(), is(equalTo(3)));
        assertThat(actual, hasItems(expected.toArray(new String[0])));
    }
}
