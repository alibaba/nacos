FROM java:8-jre-alpine
ADD ./docker-entrypoint.sh /docker-entrypoint.sh
ADD nacos-server.zip /home/nacos-server.zip
RUN unzip /home/nacos-server.zip && rm /home/nacos-server.zip
ENTRYPOINT ["/docker-entrypoint.sh"]


