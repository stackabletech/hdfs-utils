package tech.stackable.hadoop;

import org.apache.hadoop.security.UserGroupInformation;

import java.io.IOException;
import java.util.List;

public class OpaQueryUgi {
    public UserGroupInformation realUser;
    public String userName;
    public String shortUserName;

    public String primaryGroup;
    public List<String> groups;

    public UserGroupInformation.AuthenticationMethod authenticationMethod;
    public UserGroupInformation.AuthenticationMethod realAuthenticationMethod;

    public OpaQueryUgi(UserGroupInformation ugi) {
        this.realUser = ugi.getRealUser();
        this.userName = ugi.getUserName();
        this.shortUserName = ugi.getShortUserName();
        try {
            this.primaryGroup = ugi.getPrimaryGroupName();
        } catch (IOException e) {
            this.primaryGroup = null;
        }
        this.groups = ugi.getGroups();
        this.authenticationMethod = ugi.getAuthenticationMethod();
        this.realAuthenticationMethod = ugi.getRealAuthenticationMethod();
    }
}
