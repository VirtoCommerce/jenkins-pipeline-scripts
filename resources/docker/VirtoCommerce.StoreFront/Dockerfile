# escape=`

FROM microsoft/aspnet:4.6.2
MAINTAINER Alexander Siniouguine <support@virtocommerce.com>

ARG SOURCE=VirtoCommerce.Storefront

ADD $SOURCE c:\vc-storefront

# Install Visual C++ 2017 Redistributable Packages
#ADD https://go.microsoft.com/fwlink/?LinkId=746571 /vc_redist.x86.exe
ADD https://go.microsoft.com/fwlink/?LinkId=746572 /vc_redist.x64.exe

# Install Visual C++ 2015 Redistributable Packages
#ADD https://download.microsoft.com/download/6/D/F/6DF3FF94-F7F9-4F0B-838C-A328D1A7D0EE/vc_redist.x86.exe /vc_redist.x86.exe
#ADD https://download.microsoft.com/download/6/D/F/6DF3FF94-F7F9-4F0B-838C-A328D1A7D0EE/vc_redist.x64.exe /vc_redist.x64.exe

# Install Visual C++ 2013 Redistributable Packages
#RUN Invoke-WebRequest "https://download.microsoft.com/download/2/E/6/2E61CFA4-993B-4DD4-91DA-3737CD5CD6E3/vcredist_x86.exe" -OutFile "c:\vc_redist.x86.exe"
#ADD https://download.microsoft.com/download/2/E/6/2E61CFA4-993B-4DD4-91DA-3737CD5CD6E3/vcredist_x64.exe /vc_redist.x64.exe
#ADD https://download.microsoft.com/download/2/E/6/2E61CFA4-993B-4DD4-91DA-3737CD5CD6E3/vcredist_x86.exe /vc_redist.x86.exe

RUN C:\vc_redist.x64.exe /quiet /install
#RUN C:\vc_redist.x86.exe /quiet /install

# Configure website
RUN Remove-Website -Name 'Default Web Site'; `  
    New-Website -Name 'vc-storefront' `
                -Port 80 -PhysicalPath 'c:\vc-storefront' `
                -ApplicationPool 'DefaultAppPool'