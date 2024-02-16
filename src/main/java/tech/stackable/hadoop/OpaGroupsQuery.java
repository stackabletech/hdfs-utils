package tech.stackable.hadoop;

import java.util.StringJoiner;

public class OpaGroupsQuery {
  public final OpaGroupsQueryInput input;

  public OpaGroupsQuery(OpaGroupsQueryInput input) {
    this.input = input;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", OpaGroupsQuery.class.getSimpleName() + "[", "]")
        .add("input=" + input)
        .toString();
  }

  public static class OpaGroupsQueryInput {
    public final String username;

    public OpaGroupsQueryInput(String user) {
      this.username = user;
    }

    @Override
    public String toString() {
      return new StringJoiner(", ", OpaGroupsQueryInput.class.getSimpleName() + "[", "]")
          .add("username='" + username + "'")
          .toString();
    }
  }
}
