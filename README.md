# Stackable Group Mapper for Apache Hadoop

HDFS internally uses user groups for group permissions on folders etc. For this reason it is not enough to just have the groups in OPA during authorization, but they actually need to be available to Hadoop. Hadoop offers a few default group providers, such as:

* LDAP
* Linux user group (usually provided by SSSD or Centrify or similar tools)

Hadoop exposes an [interface](https://github.com/apache/hadoop/blob/rel/release-3.3.6/hadoop-common-project/hadoop-common/src/main/java/org/apache/hadoop/security/GroupMappingServiceProvider.java) that users can implement to extend these [group mappings](https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/GroupsMapping.html). The Stackable Group Mapper does this to look up user groups from OPA.

## OPA Mappings

OPA mappings are returned from the [User-Info-Fetcher](https://docs.stackable.tech/home/nightly/opa/usage-guide/user-info-fetcher#_example_rego_rule) in this form:

```json
{
  "id": "af07f12c-a2db-40a7-93e0-874537bdf3f5",
  "username": "alice",
  "groups": [
    "/superset-admin"
  ],
  "customAttributes": {}
}
```

The Group Mapper only needs the group listing, which can be requested specifically from the Opa server by providing the current user and filtering out the groups with the `json.filter` function, returning a segment that looks like this:

```json
{
  "result": {
    "groups": {
      "groups": [
        "/admin",
        "/superuser"
      ]
    }
  }
}
```

The leading slash is required by Opa/Keycloak to allow the definition of subgroups, but this is removed by the group mapper before returning this list of strings to the internal calling routine.

## Configuration

Group mappings are resolved on the NameNode and the following configuration should be added to the NameNode role:

### envOverrides

#### HADOOP_CLASSPATH

* Fixed value of `"/stackable/hadoop/share/hadoop/tools/lib/*.jar"`

### configOverrides / `core-site.xml`

#### hadoop.security.group.mapping

* Fixed value of `"tech.stackable.hadoop.StackableGroupMapper"`

#### hadoop.security.group.mapping.opa.url

* The Opa Server endpoint e.g. `"http://test-opa.default.svc.cluster.local:8081/v1/data/hdfs"`

#### hadoop.user.group.static.mapping.overrides

* The hdfs-operator will add a default static mapping whenever kerberos is activated. This should be removed so that the mapping implementation can provide this information instead: i.e. with an empty string `""`

## Testing

CRDs for spinning up test infrastructure are provided in `test/stack`. The Tiltfile will deploy these resources, build and copy the mapper to the docker image, and re-deploy the image to the running HdfsCluster.
