FROM mcr.microsoft.com/dotnet/core/aspnet:3.1

ARG SOURCE=VirtoCommerce.Platform

ADD $SOURCE /vc-platform/

WORKDIR /vc-platform

ENTRYPOINT ["dotnet", "VirtoCommerce.Platform.Web.dll"]
