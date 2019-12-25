FROM mcr.microsoft.com/dotnet/core/aspnet:2.2

ARG SOURCE=VirtoCommerce.Platform

ADD $SOURCE c:\vc-platform

WORKDIR /vc-platform
ENTRYPOINT ["dotnet", "VirtoCommerce.Platform.Web.dll"]