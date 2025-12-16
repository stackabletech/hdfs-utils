#
# cd test/stack/teragen
# docker build . -t oci.stackable.tech/sandbox/spark-k8s:3.5.6-stackable0.0.0-terasort
#

FROM oci.stackable.tech/sdp/spark-k8s:3.5.6-stackable0.0.0-dev

# this .jar is compiled from the code here: https://github.com/ehiggs/spark-terasort
COPY --chown=stackable:stackable ./spark-terasort-1.2-SNAPSHOT.jar /tmp/
