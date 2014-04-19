FROM dockerfile/java
RUN curl https://raw.githubusercontent.com/technomancy/leiningen/77d659e6eec73d1d46b890838d62590751c94844/bin/lein > /bin/lein
RUN chmod a+x /bin/lein
RUN apt-get install -y rubygems
RUN gem install foreman

RUN mkdir /app
ADD . /app

RUN adduser --disabled-password --gecos "" dpl
RUN chown dpl /app
USER dpl
WORKDIR /app

# ENTRYPOINT ["foreman" "start" "-f" "/app/Procfile.dev"]
