FROM dockerfile/java

ENV LEIN_ROOT 1

# install and setup ssh
RUN apt-get install -y openssh-server
RUN mkdir /var/run/sshd
RUN mkdir -p /root/.ssh
ADD authorized_keys /root/.ssh/authorized_keys
RUN chmod 700 /root/.ssh
RUN chmod 600 /root/.ssh/authorized_keys
RUN env > /root/.ssh/environment
RUN chmod 600 /root/.ssh/environment

RUN printf 'root:' > /root/passwdfile
RUN head -c 16 /dev/urandom  | sha1sum | cut -c1-10 >> /root/passwdfile

RUN curl https://raw.githubusercontent.com/technomancy/leiningen/77d659e6eec73d1d46b890838d62590751c94844/bin/lein > /bin/lein
RUN chmod a+x /bin/lein

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

RUN apt-get install ruby1.9.1 ruby1.9.1-dev \
  rubygems1.9.1 irb1.9.1 ri1.9.1 rdoc1.9.1 \
  build-essential libopenssl-ruby1.9.1 libssl-dev zlib1g-dev

RUN update-alternatives --install /usr/bin/ruby ruby /usr/bin/ruby1.9.1 400 \
         --slave   /usr/share/man/man1/ruby.1.gz ruby.1.gz \
                        /usr/share/man/man1/ruby1.9.1.1.gz \
        --slave   /usr/bin/ri ri /usr/bin/ri1.9.1 \
        --slave   /usr/bin/irb irb /usr/bin/irb1.9.1 \
        --slave   /usr/bin/rdoc rdoc /usr/bin/rdoc1.9.1

# choose your interpreter
# changes symlinks for /usr/bin/ruby , /usr/bin/gem
# /usr/bin/irb, /usr/bin/ri and man (1) ruby
RUN update-alternatives --config ruby
RUN update-alternatives --config gem
RUN gem install foreman

RUN lein deps
RUN lein cljx once
RUN lein cljsbuild once

EXPOSE 3000
EXPOSE 22
CMD ["/usr/local/bin/foreman", "start", "-f", "/app/Procfile.dev"]
