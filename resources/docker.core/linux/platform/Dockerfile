FROM mcr.microsoft.com/dotnet/core/aspnet:3.1

ARG SOURCE=VirtoCommerce.Platform.zip

ADD $SOURCE /vc-platform/

WORKDIR /vc-platform

RUN /bin/bash -c "apt update && apt install unzip && unzip ${SOURCE}"

ENTRYPOINT ["dotnet", "VirtoCommerce.Platform.Web.dll"]
