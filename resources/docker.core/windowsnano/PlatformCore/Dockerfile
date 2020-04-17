FROM mcr.microsoft.com/dotnet/core/aspnet:3.1

RUN mkdir C:\certs
ADD devcert.pfx certs

ENV Kestrel__Certificates__Default__Path C:\\certs\\devcert.pfx
ENV Kestrel__Certificates__Default__Password virto

ARG SOURCE=VirtoCommerce.Platform

ADD $SOURCE c:\\vc-platform

WORKDIR /vc-platform
ENTRYPOINT ["dotnet", "VirtoCommerce.Platform.Web.dll"]