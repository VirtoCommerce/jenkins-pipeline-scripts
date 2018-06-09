# escape=`

FROM microsoft/dotnet:2.1-aspnetcore-runtime
MAINTAINER Alexander Siniouguine <support@virtocommerce.com>

ARG SOURCE=VirtoCommerce.Storefront

ADD $SOURCE c:\vc-storefront-core

# Install Visual C++ 2017 Redistributable Packages
RUN Invoke-WebRequest "https://go.microsoft.com/fwlink/?LinkId=746572" -OutFile "c:\vc_redist.x64.exe"

RUN powershell start-process C:\vc_redist.x64.exe -ArgumentList '/quiet' -Wait

WORKDIR /vc-storefront-core
ENTRYPOINT ["dotnet", "VirtoCommerce.Storefront.dll"]