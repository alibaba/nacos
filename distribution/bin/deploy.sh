sudo -u admin rm -rf ../target/nacos-console-0.1.0.jar
sudo -u admin scp xingxuechao@$1:/Users/xingxuechao/Documents/source/gitlab/opensource/nacos/console/target/nacos-console-0.1.0.jar /home/admin/nacos/target
sudo -u admin sh shutdown.sh
sudo -u admin sh startup.sh