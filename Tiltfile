k8s_yaml('test/stack/01-install-krb5-kdc.yaml')
k8s_yaml('test/stack/02-create-kerberos-secretclass.yaml')
k8s_yaml('test/stack/05-opa.yaml')
k8s_yaml('test/stack/10-hdfs.yaml')

local_resource(
  'compile authorizer',
  'mvn package -DskipTests',
  deps=['src', 'pom.xml'])

docker_build(
  'hdfs',
  './target',
  dockerfile='./Dockerfile')

k8s_kind('HdfsCluster', image_json_path='{.spec.image.custom}')