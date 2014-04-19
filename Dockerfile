FROM dockerfile/java
RUN curl https://raw.githubusercontent.com/technomancy/leiningen/77d659e6eec73d1d46b890838d62590751c94844/bin/lein > /bin/lein
RUN chmod a+x /bin/lein
RUN apt-get install -y rubygems
RUN gem install foreman

RUN adduser --disabled-password --gecos "" --home=/app  dpl
ADD . /app

USER dpl
CMD foreman start -f Procfile.dev
