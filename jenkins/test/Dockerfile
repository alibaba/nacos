FROM java:8-jre-alpine
ADD ./docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod a+x /docker-entrypoint.sh
ADD nacos-server.zip /home/nacos-server.zip
RUN unzip /home/nacos-server.zip && rm /home/nacos-server.zip
ENTRYPOINT ["/bin/bash"]


