package org.cru.contentscoring.core.webservices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SetMessageBodyReader implements MessageBodyReader<Set> {
    @Override
    public boolean isReadable(
        final Class<?> aClass,
        final Type type,
        final Annotation[] annotations,
        final MediaType mediaType) {

        if (aClass.equals(Set.class)) {
            if (type != null) {
                return isSetOfStrings(type);
            }
        }
        return false;
    }

    @Override
    public Set readFrom(
        final Class<Set> aClass,
        final Type type,
        final Annotation[] annotations,
        final MediaType mediaType,
        final MultivaluedMap<String, String> multivaluedMap,
        final InputStream inputStream) throws WebApplicationException {

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        List<String> lines = bufferedReader.lines().collect(Collectors.toList());

        Set<String> set = new HashSet<>();
        for (String line : lines) {
            set.addAll(deserializeSet(line));
        }

        return set;
    }

    private boolean isSetOfStrings(final Type type) {
        if (type != null) {
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                return parameterizedType.getActualTypeArguments().length == 1
                    && parameterizedType.getActualTypeArguments()[0].equals(String.class);
            }
        }
        return false;
    }

    private Set<String> deserializeSet(final String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        Set<String> set = new HashSet<>();
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            if (jsonNode.isArray()) {
                Iterator<JsonNode> iterator = jsonNode.elements();
                while (iterator.hasNext()) {
                    JsonNode node = iterator.next();
                    set.add(node.textValue());
                }
            } else {
                set.add(jsonNode.textValue());
            }
            return set;
        } catch (Exception e) {
            return new HashSet<>();
        }
    }
}
