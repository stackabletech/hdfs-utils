FROM oci.stackable.tech/sdp/hadoop:3.4.0-stackable0.0.0-dev@sha256:4b4e65f2186190a6796c5e9b8b3b60870c65c46e7cc9e39ad98818459def21ee

# Remove existing hdfs-utils jars, so we can ship our custom one
RUN rm -f /stackable/hadoop/share/hadoop/common/lib/hdfs-utils-*.jar
RUN rm -f /stackable/hadoop/share/hadoop/tools/lib/hdfs-utils-*.jar

COPY --chown=stackable:stackable ./hdfs-utils-*.jar /stackable/hadoop/share/hadoop/common/lib/
COPY --chown=stackable:stackable ./bom.json /stackable/hadoop/share/hadoop/common/lib/hdfs-utils.cdx.json
