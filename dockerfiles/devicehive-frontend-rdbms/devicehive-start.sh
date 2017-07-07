#!/bin/bash -e

set -x

trap 'terminate' TERM INT

terminate() {
   echo "SIGTERM received, terminating $PID"
   kill -TERM $PID
   wait $PID
}

# Check if all required parameters are set
if [ -z "$DH_ZK_ADDRESS" \
  -o -z "$DH_KAFKA_ADDRESS" \
  -o -z "$DH_POSTGRES_ADDRESS" \
  -o -z "$DH_POSTGRES_USERNAME" \
  -o -z "$DH_POSTGRES_PASSWORD" \
  -o -z "$DH_POSTGRES_DB" ]
then
    echo "Some of required environment variables are not set or empty."
    echo "Please check following vars are passed to container:"
    echo "- DH_ZK_ADDRESS"
    echo "- DH_KAFKA_ADDRESS"
    echo "- DH_POSTGRES_ADDRESS"
    echo "- DH_POSTGRES_USERNAME"
    echo "- DH_POSTGRES_PASSWORD"
    echo "- DH_POSTGRES_DB"
    exit 1
fi

echo "Starting DeviceHive frontend"
java -server -Xmx512m -XX:MaxRAMFraction=1 -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=70 -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark -jar \
-Dcom.devicehive.log.level=${DH_LOG_LEVEL:-INFO} \
-Droot.log.level=${ROOT_LOG_LEVEL:-WARN} \
-Dspring.datasource.url=jdbc:postgresql://${DH_POSTGRES_ADDRESS}:${DH_POSTGRES_PORT:-5432}/${DH_POSTGRES_DB} \
-Dspring.datasource.username="${DH_POSTGRES_USERNAME}" \
-Dspring.datasource.password="${DH_POSTGRES_PASSWORD}" \
-Dbootstrap.servers=${DH_KAFKA_ADDRESS}:${DH_KAFKA_PORT:-9092} \
-Dzookeeper.connect=${DH_ZK_ADDRESS}:${DH_ZK_PORT:-2181} \
-Drpc.client.response-consumer.threads=${DH_RPC_CLIENT_RES_CONS_THREADS:-1} \
-Dserver.context-path=/api \
-Dserver.port=8080 \
./devicehive-frontend-${DH_VERSION}-boot.jar &
PID=$!
wait $PID