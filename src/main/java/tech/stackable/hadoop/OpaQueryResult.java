package tech.stackable.hadoop;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.hadoop.util.Lists;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public class OpaQueryResult {
    public Map<String, Map<String, Object>> result;
    public List<String> groups;

    @SuppressWarnings("unchecked")
    @JsonProperty("result")
    private void unpackNested(Map<String, Map<String, Object>> result) {
        this.result = result;
        List<String> rawGroups = Lists.newArrayList();
        for (String group :  (List<String>) result.get("groups").get("groups")) {
            rawGroups.add(removeSlashes.apply(group));
        }

        this.groups = rawGroups;
    }

    private final static UnaryOperator<String> removeSlashes = s -> {
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    };
}
