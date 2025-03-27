## Starting RabbitMQ with Streams Enabled
Let's start a RabbitMQ Docker container:
```shell
docker run -it --rm --name rabbitmq -p 5552:5552 \
-e RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS='-rabbitmq_stream advertised_host localhost' \
rabbitmq:3.9
```
Streams ship as a core plugin in RabbitMQ 3.9, so we have to make sure this plugin is enabled. 
Open a new terminal tab and execute the following command:
```shell
docker exec rabbitmq rabbitmq-plugins enable rabbitmq_stream
```
Follow to dive deep, follow this link https://www.rabbitmq.com/blog/2021/07/19/rabbitmq-streams-first-application


