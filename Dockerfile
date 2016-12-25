FROM mhart/alpine-node

RUN apk update && apk add git && apk add curl && apk add openjdk8
# Create app directory
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
RUN git clone https://github.com/Netflix/vizceral-example.git  .
RUN npm install

RUN curl -OL https://github.com/OskarKjellin/vizceral-hystrix/releases/download/v1.0.6/vizceral-hystrix-1.0.6.jar
COPY src/main/js/trafficFlow.jsx /usr/src/app/src/components/trafficFlow.jsx

EXPOSE 8080 8081
ADD config.json config.json
RUN echo "java -jar /usr/src/app/vizceral-hystrix-1.0.6.jar config.json&" > start.sh && echo "npm run dev" >> start.sh && chmod +x start.sh

ENTRYPOINT "/usr/src/app/start.sh"
#CMD [ "npm", "run", "dev" ]
