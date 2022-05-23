ARG APP_INSIGHTS_AGENT_VERSION=2.5.1

FROM hmctspublic.azurecr.io/base/java:openjdk-11-distroless-1.2

COPY lib/AI-Agent.xml /opt/app/
COPY build/libs/ccd-message-publisher.jar /opt/app/

USER hmcts

EXPOSE 4456
CMD [ "ccd-message-publisher.jar" ]
