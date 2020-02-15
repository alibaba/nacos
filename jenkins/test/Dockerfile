FROM java:8-jre-alpine
ADD ./docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod a+x /docker-entrypoint.sh
ADD nacos-server.zip /nacos-server.zip
RUN unzip /nacos-server.zip && rm /nacos-server.zip
ENTRYPOINT ["/docker-entrypoint.sh"]


