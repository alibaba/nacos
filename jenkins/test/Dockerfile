FROM java:8-jre-alpine
ADD nacos-server.zip /home/nacos-server.zip
RUN unzip /home/nacos-server.zip && rm /home/nacos-server.zip
ENTRYPOINT ["echo 'Starting nacos' && cd /home/nacos/bin && ./startup.sh -m standalone && cd ../logs && tail -f start.out"]


