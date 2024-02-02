package tech.stackable.hadoop;

import java.util.StringJoiner;

public class OpaQuery {
  public final OpaQueryInput input;

  public OpaQuery(OpaQueryInput input) {
    this.input = input;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", OpaQuery.class.getSimpleName() + "[", "]")
        .add("input=" + input)
        .toString();
  }

  public static class OpaQueryInput {
    public final String username;

    public OpaQueryInput(String user) {
      this.username = user;
    }

    @Override
    public String toString() {
      return new StringJoiner(", ", OpaQueryInput.class.getSimpleName() + "[", "]")
          .add("username='" + username + "'")
          .toString();
    }
  }
}
