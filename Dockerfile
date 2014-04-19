FROM dockerfile/java

# install and setup ssh
RUN apt-get install -y openssh-server
RUN mkdir /var/run/sshd
RUN mkdir -p /root/.ssh
ADD authorized_keys /root/.ssh/authorized_keys
RUN chmod 700 /root/.ssh
RUN chmod 600 /root/.ssh/authorized_keys

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
RUN lein cljx once
RUN lein cljsbuild once

EXPOSE 3000
EXPOSE 22
CMD ["/usr/local/bin/foreman", "start", "-f", "/app/Procfile.dev"]
