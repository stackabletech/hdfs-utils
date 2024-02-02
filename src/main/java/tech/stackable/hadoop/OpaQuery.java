package tech.stackable.hadoop;

public class OpaQuery {
  public final OpaQueryInput input;

  public OpaQuery(OpaQueryInput input) {
    this.input = input;
  }

  public static class OpaQueryInput {
    public final String username;

    public OpaQueryInput(String user) {
      this.username = user;
    }
  }
}
