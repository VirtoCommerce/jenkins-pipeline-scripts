FROM mcr.microsoft.com/dotnet/core/aspnet:3.1

ARG SOURCE=VirtoCommerce.Platform
ARG WAIT_VERSION=2.7.3

ADD https://github.com/ufoscout/docker-compose-wait/releases/download/$WAIT_VERSION/wait /wait
RUN chmod +x /wait
ADD $SOURCE /opt/vc-platform/

WORKDIR /opt/vc-platform

ENTRYPOINT ["dotnet", "VirtoCommerce.Platform.Web.dll"]