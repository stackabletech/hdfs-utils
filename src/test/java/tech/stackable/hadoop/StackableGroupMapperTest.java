package tech.stackable.hadoop;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

public class StackableGroupMapperTest {

    @Test
    public void testJsonMapper() throws JsonProcessingException {
        String input = "{\n" +
                "    \"result\": {\n" +
                "        \"groups\": {\n" +
                "            \"groups\": [\n" +
                "                \"/admin\",\n" +
                "                \"/superuser\"\n" +
                "            ]\n" +
                "        },\n" +
                "        \"users_by_name\": {\n" +
                "            \"alice\": {\n" +
                "                \"customAttributes\": {},\n" +
                "                \"groups\": [\n" +
                "                    \"/superset-admin\"\n" +
                "                ],\n" +
                "                \"id\": \"af07f12c-1234-40a7-93e0-874537bdf3f5\",\n" +
                "                \"username\": \"alice\"\n" +
                "            },\n" +
                "            \"bob\": {\n" +
                "                \"customAttributes\": {},\n" +
                "                \"groups\": [\n" +
                "                    \"/admin\"\n" +
                "                ],\n" +
                "                \"id\": \"af07f12c-2345-40a7-93e0-874537bdf3f5\",\n" +
                "                \"username\": \"bob\"\n" +
                "            },\n" +
                "            \"stackable\": {\n" +
                "                \"customAttributes\": {},\n" +
                "                \"groups\": [\n" +
                "                    \"/admin\",\n" +
                "                    \"/superuser\"\n" +
                "                ],\n" +
                "                \"id\": \"af07f12c-3456-40a7-93e0-874537bdf3f5\",\n" +
                "                \"username\": \"stackable\"\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ObjectMapper json = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        OpaQueryResult result = json.readValue(input, OpaQueryResult.class);
        Assert.assertEquals("admin", result.groups.get(0));
        Assert.assertEquals("superuser", result.groups.get(1));
    }
}