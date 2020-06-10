FROM mcr.microsoft.com/dotnet/core/aspnet:3.1

ARG SOURCE=VirtoCommerce.Platform

ADD $SOURCE /opt/vc-platform/

WORKDIR /opt/vc-platform

ENTRYPOINT ["dotnet", "VirtoCommerce.Platform.Web.dll"]