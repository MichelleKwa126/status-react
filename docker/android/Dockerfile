# This image is only for extracting NDK and SDK
FROM ubuntu:16.04 AS sdk_and_ndk

ARG ANDROID_NDK_VERSION
ARG ANDROID_SDK_VERSION

ENV SDK_PACKAGES emulator patcher;v4 build-tools;26.0.2 platforms;android-26 platforms;android-27 platforms;android-28 extras;android;m2repository extras;google;m2repository

RUN apt-get update \
 && apt-get -q -y --no-install-recommends install software-properties-common \
 && add-apt-repository -y ppa:webupd8team/java \
 && apt-get update \
 && echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections \
 && echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections \
 && DEBIAN_FRONTEND=noninteractive \
      apt-get -q -y --no-install-recommends install \
        unzip oracle-java8-installer oracle-java8-set-default ca-certificates \
 && apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /usr/share/man

COPY sdk-tools-linux-${ANDROID_SDK_VERSION}.zip /tmp/sdk-tools-linux.zip

RUN unzip -q /tmp/sdk-tools-linux.zip -d /usr/lib/android-sdk \
 && mkdir ~/.android && touch ~/.android/repositories.cfg \
 && yes | /usr/lib/android-sdk/tools/bin/sdkmanager --licenses > /dev/null \
 && for PKG in ${SDK_PACKAGES}; do /usr/lib/android-sdk/tools/bin/sdkmanager --install "${PKG}"; done \
 && cd /usr/lib/android-sdk \
 && rm -fr extras emulator ndk-bundle/toolchains/{aarch64-linux-android-4.9,x86-4.9,x86_64-4.9,llvm} \
 && chmod 777 -R /usr/lib/android-sdk

COPY android-ndk-${ANDROID_NDK_VERSION}-linux-x86_64.zip /tmp/android-ndk-linux.zip

RUN unzip -q /tmp/android-ndk-linux.zip -d /usr/lib \
 && ln -sf /usr/lib/android-ndk-${ANDROID_NDK_VERSION} /usr/lib/android-ndk \
 && cd /usr/lib/android-ndk \
 && rm -fr docs tests samples \
 && find sources -mindepth 2 -maxdepth 2 | grep -v 'gnu-libstdc' | xargs rm -fr \
 && find toolchains -mindepth 1 -maxdepth 1 | grep -vE '(arm|aarch|x86|mips).*4.(8|9)' | xargs rm -fr \
 && find prebuilt -mindepth 1 -maxdepth 1 | grep -vE '(android-arm|linux-x86)' | xargs rm -fr \
 && find platforms -mindepth 1 -maxdepth 1 | grep -v android-21 | xargs rm -fr \
 && chmod 777 -R /usr/lib/android-ndk

################################################################################
FROM ubuntu:16.04

ARG NVM_VERSION
ARG NVM_NODE_VERSION
ARG LEIN_VERSION
ARG ANDROID_NDK_VERSION
ARG ANDROID_SDK_VERSION

# We have to do this because Jenkins doesn't let us
# https://issues.jenkins-ci.org/browse/JENKINS-49076
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8 \
    LANGUAGE=en_US.UTF-8 \
    LEIN_HOME=/var/tmp/lein \
    NPM_CONFIG_CACHE=/var/tmp/npm \
    YARN_CACHE_FOLDER=/var/tmp/yarn \
    ANDROID_HOME=/usr/lib/android-sdk \
    ANDROID_SDK_ROOT=/usr/lib/android-sdk \
    ANDROID_NDK=/usr/lib/android-ndk \
    ANDROID_NDK_HOME=/usr/lib/android-ndk

RUN mkdir /var/tmp/npm /var/tmp/yarn /var/tmp/lein \
 && chmod 777 /var/tmp/npm /var/tmp/yarn /var/tmp/lein 

RUN apt-get update && apt-get -q -y --no-install-recommends install curl software-properties-common \
 && curl -sL https://deb.nodesource.com/setup_10.x | bash - \
 && curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - \
 && echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list \
 && add-apt-repository -y ppa:webupd8team/java \
 && add-apt-repository -y ppa:cwchien/gradle \
 && apt-get remove -y software-properties-common \
 && apt-get update \
 && echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections \
 && echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections \
 && DEBIAN_FRONTEND=noninteractive \
      apt-get -q -y --no-install-recommends install \
        file git wget zip unzip s3cmd build-essential nodejs yarn \
        gradle ruby ruby-dev ruby-bundler locales ca-certificates \
        oracle-java8-installer oracle-java8-set-default \
 && locale-gen ${LANG} \
 && apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /usr/share/man

# Leiningen setup
RUN wget -q https://raw.githubusercontent.com/technomancy/leiningen/${LEIN_VERSION}/bin/lein -O /usr/local/bin/lein \
 && chmod 755 /usr/local/bin/lein

# Gems setup
RUN gem install cocoapods bundler fastlane fastlane-plugin-diawi fastlane-plugin-clean_testflight_testers

# These are the UID and GID values used by Jenkins
RUN addgroup --gid 1002 jenkins \
 && adduser --shell /bin/bash \
      --disabled-password --gecos "" \
      --uid 1001 --gid 1002 jenkins \
 && su jenkins -c "git config --global user.email 'jenkins'" \
 && su jenkins -c "git config --global user.name 'jenkins@status.im'"

# Install Android SDK & NDK
COPY --from=sdk_and_ndk /usr/lib/android-sdk /usr/lib/android-sdk 
COPY --from=sdk_and_ndk /usr/lib/android-ndk /usr/lib/android-ndk 
RUN chmod o+w /usr/lib/android-sdk /usr/lib/android-ndk

# Install NVM for Jenkins
RUN su jenkins -c "\
    curl -s -o- https://raw.githubusercontent.com/creationix/nvm/${NVM_VERSION}/install.sh | bash && \
    source ~/.nvm/nvm.sh && nvm install ${NVM_NODE_VERSION} && nvm alias default ${NVM_NODE_VERSION}"

LABEL source="https://github.com/status-im/status-react/tree/develop/docker" \
      description="Image for building Android version of Status app." \
      maintainer="jakub@status.im"
