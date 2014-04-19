FROM dockerfile/java

RUN curl https://raw.githubusercontent.com/technomancy/leiningen/77d659e6eec73d1d46b890838d62590751c94844/bin/lein > /bin/lein
RUN chmod a+x /bin/lein
ENV LEIN_ROOT 1
RUN lein upgrade


ADD . /app/
WORKDIR /app/

# Install Node.js
RUN apt-get install -y software-properties-common
RUN add-apt-repository -y ppa:chris-lea/node.js
RUN apt-get update
RUN apt-get install -y nodejs
# Append to $PATH variable.
RUN echo '\n# Node.js\nexport PATH="node_modules/.bin:$PATH"' >> /root/.bash_profile

RUN npm install supervisor -g

RUN apt-get install -y rubygems
RUN gem install foreman

RUN lein deps

CMD ["/usr/local/bin/foreman", "start", "-f", "/app/Procfile.dev"]
