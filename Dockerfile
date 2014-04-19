FROM dockerfile/java
RUN curl https://raw.githubusercontent.com/technomancy/leiningen/77d659e6eec73d1d46b890838d62590751c94844/bin/lein > /bin/lein
RUN chmod a+x /bin/lein
RUN apt-get install -y rubygems
RUN gem install foreman

RUN adduser --disabled-password --gecos "" --home /app  dpl
RUN chown -R dpl /app
ADD . /app

USER dpl

ENTRYPOINT ["foreman" "start" "-f" "/app/Procfile.dev"]
