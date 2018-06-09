# escape=`

FROM microsoft/aspnet:4.6.2
MAINTAINER Alexander Siniouguine <support@virtocommerce.com>

ARG SOURCE=VirtoCommerce.Storefront

ADD $SOURCE c:\vc-storefront

# Install Visual C++ 2013 Redistributable Packages
RUN Invoke-WebRequest "https://download.microsoft.com/download/2/E/6/2E61CFA4-993B-4DD4-91DA-3737CD5CD6E3/vcredist_x64.exe" -OutFile "c:\vc_redist.x64.exe"

RUN powershell start-process C:\vc_redist.x64.exe -ArgumentList '/quiet' -Wait

# Configure website
RUN Remove-Website -Name 'Default Web Site'; `  
    New-Website -Name 'vc-storefront' `
                -Port 80 -PhysicalPath 'c:\vc-storefront' `
                -ApplicationPool 'DefaultAppPool'