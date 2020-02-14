FROM java:8-jre-alpine
ADD nacos-server.tar.gz /home/nacos-server.tar.gz
RUN tar -zxf /home/nacos-server.tar.gz && rm /home/nacos-server.tar.gz
ENTRYPOINT ["echo 'Starting nacos' && cd /home/nacos/bin && ./startup.sh -m standalone && cd ../logs && tail -f start.out"]


