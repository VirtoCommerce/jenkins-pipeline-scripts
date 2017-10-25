# escape=`
# Build runtime image
FROM microsoft/aspnetcore:2.0
MAINTAINER Alexander Siniouguine <support@virtocommerce.com>

RUN Invoke-WebRequest "https://download.microsoft.com/download/2/E/6/2E61CFA4-993B-4DD4-91DA-3737CD5CD6E3/vcredist_x64.exe" -OutFile "c:\vc_redist.x64.exe"
RUN powershell start-process C:\vc_redist.x64.exe -ArgumentList '/quiet' -Wait
ARG SOURCE=VirtoCommerce.Storefront\bin\Debug\netcoreapp2.0\publish
WORKDIR /app
COPY $SOURCE .
ENTRYPOINT ["dotnet", "VirtoCommerce.Storefront.dll"]