#
# cd test/stack/teragen
# docker build . -t oci.stackable.tech/sdp/spark-k8s:3.5.8-stackable0.0.0-terasort
#

FROM oci.stackable.tech/sdp/spark-k8s:3.5.8-stackable0.0.0-dev

# this .jar is compiled from the code here: https://github.com/ehiggs/spark-terasort
# N.B. 3.5.8 is used as that compiles against scala 2.12, as is the case for the terasort jar.
# For newer spark versions (3.5.8 is marked for removal in 26.11), the tereasort code will need
# to be changed to compile against scala 2.13.
COPY --chown=stackable:stackable ./spark-terasort-1.2-SNAPSHOT.jar /tmp/
