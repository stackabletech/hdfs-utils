package tech.stackable.hadoop;

import java.io.IOException;
import java.util.List;
import org.apache.hadoop.security.UserGroupInformation;

public class OpaQueryUgi {
  // Wrapping this
  public OpaQueryUgi realUser;
  public String userName;
  public String shortUserName;

  public String primaryGroup;
  public List<String> groups;

  public UserGroupInformation.AuthenticationMethod authenticationMethod;
  public UserGroupInformation.AuthenticationMethod realAuthenticationMethod;

  /**
   * Wrapper around {@link UserGroupInformation}, which does not throw random errors during
   * serialization when no primary group is known for the user. "Caused by:
   * com.fasterxml.jackson.databind.JsonMappingException: Unexpected IOException (of type
   * java.io.IOException): There is no primary group for UGI
   * hive/hive-iceberg.default.svc.cluster.local@KNAB.COM (auth:KERBEROS)"
   */
  public OpaQueryUgi(UserGroupInformation ugi) {
    UserGroupInformation realUser = ugi.getRealUser();
    if (realUser != null) {
      this.realUser = new OpaQueryUgi(ugi.getRealUser());
    } else {
      this.realUser = null;
    }
    userName = ugi.getUserName();
    shortUserName = ugi.getShortUserName();
    try {
      primaryGroup = ugi.getPrimaryGroupName();
    } catch (IOException e) {
      primaryGroup = null;
    }
    groups = ugi.getGroups();
    authenticationMethod = ugi.getAuthenticationMethod();
    realAuthenticationMethod = ugi.getRealAuthenticationMethod();
  }
}
