# Stackable Group Mapper for Apache Hadoop

[Stackable Data Platform](https://stackable.tech/) | [Platform Docs](https://docs.stackable.tech/) | [Discussions](https://github.com/orgs/stackabletech/discussions) | [Discord](https://discord.gg/7kZ3BNnCAF)

This projects is a plugin for Apache Hadoop, which can look up groups for users in an OpenPolicyAgent (OPA) instance.
It is intended to be used with the [Stackable Data Platform](https://stackable.tech)

## Description

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

* The Opa Server endpoint e.g. `"http://test-opa.default.svc.cluster.local:8081/v1/data/hdfsgroups"`

#### hadoop.security.group.mapping.opa.list.name

* Opa responses have a [root field](https://www.openpolicyagent.org/docs/latest/rest-api/#response-message) called `result`: the result itself - in this case of a list of user groups - is a top-level field within the root field and is configurable i.e. the group mapper just needs to look up this field from the response and this is passed in the configuration. This means that both the output format of the rego rule and the corresponding response field are configured independently of the group mapper.

#### hadoop.user.group.static.mapping.overrides

* The hdfs-operator will add a default static mapping whenever kerberos is activated. This should be removed so that the mapping implementation can provide this information instead: i.e. with an empty string `""`

## Testing

CRDs for spinning up test infrastructure are provided in `test/stack`. The Tiltfile will deploy these resources, build and copy the mapper to the docker image, and re-deploy the image to the running HdfsCluster.

The group mapping can be verified by shelling into the namenode, requesting a kerberos ticket and then calling `hdfs groups` for the namenode user:

```shell
klist -k /stackable/kerberos/keytab
kinit -kt /stackable/kerberos/keytab nn/simple-hdfs.default.svc.cluster.local@CLUSTER.LOCAL
klist

# N.B. hadoop will replace the realm placeholder with the env-var automatically but this must be done manually if shelling into the container:
export KERBEROS_REALM=$(grep -oP 'default_realm = \K.*' /stackable/kerberos/krb5.conf)
cat /stackable/config/namenode/core-site.xml | sed -e 's/${env.KERBEROS_REALM}/'"$KERBEROS_REALM/g" > /stackable/config/namenode/core-site2.xml
mv /stackable/config/namenode/core-site2.xml /stackable/config/namenode/core-site.xml

bin/hdfs groups
```

The last command will yield something like this:

```shell
nn/simple-hdfs.default.svc.cluster.local@CLUSTER.LOCAL : admin superuser
```

and the Hadoop logs will show that the lookup has taken place:

```text
- Calling StackableGroupMapper.getGroups for user [nn]
- Opa response [{"result":{"groups":{"groups":["/admin","/superuser"]},"users_by_name":{"alice":{"customAttributes":{},"groups":["/superset-admin"],"id":"af07f12c-1234-40a7-93e0-874537bdf3f5","username":"alice"},"bob":{"customAttributes":{},"groups":["/admin"],"id":"af07f12c-2345-40a7-93e0-874537bdf3f5","username":"bob"},"nn":{"customAttributes":{},"groups":["/admin","/superuser"],"id":"af07f12c-7890-40a7-93e0-874537bdf3f5","username":"nn"},"stackable":{"customAttributes":{},"groups":["/admin","/superuser"],"id":"af07f12c-3456-40a7-93e0-874537bdf3f5","username":"stackable"}}}}
- Groups for [nn]: [[admin, superuser]]
```

