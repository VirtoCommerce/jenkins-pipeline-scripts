# escape=`
ARG RUNTIME_IMAGE=mcr.microsoft.com/dotnet/core/aspnet:3.1

FROM $RUNTIME_IMAGE

ARG SOURCE=VirtoCommerce.Storefront

ADD $SOURCE c:\vc-storefront

WORKDIR /vc-storefront
#Nano server doesn't support installs, need to copy DLLs manually
COPY libs\x64\*.dll c:\windows\SysWOW64\
COPY libs\x86\*.dll c:\windows\System32\
ENTRYPOINT ["dotnet", "VirtoCommerce.Storefront.dll"]
